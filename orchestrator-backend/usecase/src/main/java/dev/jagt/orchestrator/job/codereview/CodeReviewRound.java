package dev.jagt.orchestrator.job.codereview;

import dev.jagt.orchestrator.job.codereview.request.DiffAnchors;
import dev.jagt.orchestrator.job.codereview.request.LedgerRow;
import dev.jagt.orchestrator.job.codereview.request.NotePoster;
import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.codehost.review.NoteAnchor;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
import dev.jagt.orchestrator.service.CodeReviewSettings.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One request read once per new head: the reviews a request gets scale with its size, one per new
 * head and at most 2 plus its changed lines over 500 (an estimate). A request nobody may be noted
 * on is never read, so no model is asked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodeReviewRound {

  private static final int BASE_REVIEWS = 2;
  private static final int LINES_PER_REVIEW = 500;

  private final RequestNotes notes;
  private final ReviewLedger ledger;
  private final CodeReviewPanel panel;
  private final SubjectCheckout checkout;
  private final NotePoster poster;

  public void run(Subject subject, Settings settings, String model) {
    if (subject.own() || !settings.postOthers()) {
      return;
    }
    Optional<LedgerRow> row = ledger.row(subject.url());
    if (row.map(standing -> standing.spentUp(settings.maxCostUsd())).orElse(false)) {
      log.atDebug()
          .setMessage("code review spend ceiling reached")
          .addKeyValue("request", subject.url())
          .addKeyValue("limit", settings.maxCostUsd())
          .log();
      return;
    }
    Answer<RequestChange> read = notes.change(subject.project(), subject.url());
    if (read.facts().isEmpty()) {
      log.atWarn()
          .setMessage("code review change unread")
          .addKeyValue("request", subject.url())
          .addKeyValue("cause", read.failure())
          .addKeyValue("effect", "the request is read again on the next tick")
          .log();
      return;
    }
    RequestChange change = read.facts().get();
    if (change.state() != RequestState.OPEN) {
      log.atInfo()
          .setMessage("code review dropped a request that is over")
          .addKeyValue("request", subject.url())
          .addKeyValue("state", change.state())
          .log();
      return;
    }
    List<String> heads = row.map(LedgerRow::heads).orElse(List.of());
    if (heads.contains(change.headSha()) || heads.size() >= allowed(change)) {
      return;
    }
    if (change.diff().lines().count() > settings.maxDiffLines()) {
      log.atInfo()
          .setMessage("code review skipped a large diff")
          .addKeyValue("request", subject.url())
          .addKeyValue("limit", settings.maxDiffLines())
          .log();
      reviewed(subject, change.headSha());
      return;
    }
    judge(subject, change, model);
  }

  static int allowed(RequestChange change) {
    long changed =
        change
            .diff()
            .lines()
            .filter(line ->
                (line.startsWith("+") && !line.startsWith("+++"))
                    || (line.startsWith("-") && !line.startsWith("---")))
            .count();
    return BASE_REVIEWS + (int) (changed / LINES_PER_REVIEW);
  }

  private void judge(Subject subject, RequestChange change, String model) {
    Optional<SubjectCheckout.Open> opened = checkout.open(subject, change.headSha());
    if (opened.isEmpty()) {
      return;
    }
    try (SubjectCheckout.Open open = opened.get()) {
      Judgement judged = panel.review(subject, change, open.path(), model);
      if (!judged.failure().isBlank()) {
        log.atWarn()
            .setMessage("code review failed")
            .addKeyValue("request", subject.url())
            .addKeyValue("cause", judged.failure())
            .addKeyValue("effect", "the head stays unreviewed and is read again")
            .log();
        return;
      }
      if (post(subject, change, judged.findings())) {
        reviewed(subject, change.headSha());
      }
    }
  }

  private boolean post(Subject subject, RequestChange change, List<Finding> findings) {
    String head = change.headSha();
    for (Finding finding : findings) {
      if ("noise".equals(finding.severity())) {
        continue;
      }
      String key =
          NotePoster.digest(subject.url(), head, finding.file(), finding.anchor(), finding.kind());
      String threadKey =
          NotePoster.digest(subject.url(), finding.file(), finding.anchor(), finding.kind());
      int line = DiffAnchors.lineOf(change.diff(), finding.file(), finding.anchor());
      NotePoster.Outcome outcome =
          poster.post(
              subject,
              head,
              key,
              threadKey,
              text(finding),
              "",
              new NoteAnchor(finding.file(), line, head));
      if (outcome == NotePoster.Outcome.FAILED || outcome == NotePoster.Outcome.DROPPED) {
        return false;
      }
    }
    return true;
  }

  static String text(Finding finding) {
    return finding.file().isBlank()
        ? finding.issue()
        : "`" + finding.file() + "`: " + finding.issue();
  }

  private void reviewed(Subject subject, String head) {
    ledger.update(
        subject.url(),
        subject.project(),
        subject.taskId(),
        row -> {
          List<String> heads = new ArrayList<>(row.heads());
          heads.add(head);
          return row.withHeads(heads);
        });
  }
}

package dev.jagt.orchestrator.job.codereview;

import dev.jagt.orchestrator.job.codereview.request.NotePoster;
import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.NoteAnchor;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import dev.jagt.orchestrator.service.CodeReviewSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Answers review bots: one answer per bot note, at most three per thread. A thread past the bound
 * stays unanswered until it resolves or the request closes; a thread whose newest note is the
 * reviewer's own is left. A request whose third-party reads cost the ceiling is not answered.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotAnswers {

  static final int PER_THREAD = 3;

  private final RequestNotes notes;
  private final ReviewLedger ledger;
  private final CodeReviewPanel panel;
  private final SubjectCheckout checkout;
  private final NotePoster poster;

  public void answer(Subject subject, CodeReviewSettings.Settings settings, String model) {
    if (ledger.row(subject.url()).map(row -> row.spentUp(settings.maxCostUsd())).orElse(false)) {
      return;
    }
    Answer<List<RequestNote>> read = notes.notes(subject.project(), subject.url());
    if (read.facts().isEmpty()) {
      return;
    }
    Map<String, List<RequestNote>> threads = new LinkedHashMap<>();
    read.facts()
        .get()
        .forEach(note -> threads.computeIfAbsent(note.thread(), id -> new ArrayList<>()).add(note));
    threads.forEach((thread, inThread) -> {
      if (due(subject, thread, inThread)) {
        reply(subject, thread, inThread, model);
      }
    });
  }

  private boolean due(Subject subject, String thread, List<RequestNote> inThread) {
    RequestNote newest = inThread.getLast();
    int answered =
        ledger.row(subject.url()).map(row -> row.answers().getOrDefault(thread, 0)).orElse(0);
    return newest.kind() == AuthorKind.BOT
        && newest.resolution() != Resolution.RESOLVED
        && answered < PER_THREAD;
  }

  private void reply(Subject subject, String thread, List<RequestNote> inThread, String model) {
    Answer<RequestChange> change = notes.change(subject.project(), subject.url());
    if (change.facts().isEmpty()) {
      return;
    }
    Optional<SubjectCheckout.Open> opened = checkout.open(subject, change.facts().get().headSha());
    if (opened.isEmpty()) {
      return;
    }
    try (SubjectCheckout.Open open = opened.get()) {
      Judgement judged = panel.answer(subject, inThread, open.path(), model);
      if (!judged.failure().isBlank() || judged.findings().isEmpty()) {
        log.atWarn()
            .setMessage("code review answer failed")
            .addKeyValue("request", subject.url())
            .addKeyValue("thread", thread)
            .addKeyValue("cause", judged.failure().isBlank() ? "no answer" : judged.failure())
            .addKeyValue("effect", "the note is answered on the next read")
            .log();
        return;
      }
      RequestNote newest = inThread.getLast();
      String key = NotePoster.digest(subject.url(), "answer", newest.id());
      NotePoster.Outcome outcome =
          poster.post(
              subject,
              change.facts().get().headSha(),
              key,
              judged.findings().getFirst().issue(),
              newest.id(),
              NoteAnchor.NONE);
      if (outcome == NotePoster.Outcome.POSTED) {
        counted(subject, thread);
      }
    }
  }

  private void counted(Subject subject, String thread) {
    ledger.update(
        subject.url(),
        subject.project(),
        subject.taskId(),
        row -> {
          Map<String, Integer> answers = new LinkedHashMap<>(row.answers());
          answers.merge(thread, 1, Integer::sum);
          return row.withAnswers(answers);
        });
  }
}

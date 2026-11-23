package dev.jagt.orchestrator.job.codereview;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import dev.jagt.orchestrator.service.codereview.CodeReviewPanel;
import dev.jagt.orchestrator.service.codereview.LedgerRow;
import dev.jagt.orchestrator.service.codereview.NotePoster;
import dev.jagt.orchestrator.service.codereview.ReviewLedger;
import dev.jagt.orchestrator.service.codereview.ReviewSubjects;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads the likes, dislikes and resolutions of the notes the code reviewer posted and records a
 * line only where one changed. A request that is no longer listed is read once more and ends.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewSignalsJob implements Job {

  private final ReviewLedger ledger;
  private final ReviewSubjects subjects;
  private final RequestNotes notes;
  private final ReviewStats stats;
  private final CodeReviewPanel panel;

  @Override
  public String id() {
    return "review-signals";
  }

  @Override
  public String describe() {
    return "record the likes, dislikes and resolutions of the notes the code reviewer posted";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(10);
  }

  @Override
  public void run() {
    ReviewSubjects.Listing listing = subjects.list();
    Set<String> listed =
        listing.subjects().stream().map(subject -> subject.url()).collect(Collectors.toSet());
    for (LedgerRow row : ledger.rows()) {
      if (row.ended()) {
        continue;
      }
      boolean read = read(row);
      if (read && !listed.contains(row.url()) && listing.complete(row.project())) {
        ledger.update(row.url(), row.project(), row.taskId(), standing -> standing.withEnded(true));
      }
    }
  }

  private boolean read(LedgerRow row) {
    Answer<List<RequestNote>> read = notes.notes(row.project(), row.url());
    if (read.facts().isEmpty()) {
      return warn(row, read.failure());
    }
    Map<String, RequestNote> ours =
        read.facts().get().stream()
            .filter(note -> note.kind() == AuthorKind.ACTOR)
            .filter(note -> note.body().contains(NotePoster.MARKER))
            .collect(
                Collectors.toMap(
                    RequestNote::id, note -> note, (first, later) -> first, LinkedHashMap::new));
    if (ours.isEmpty()) {
      return true;
    }
    Answer<Map<String, Reaction>> reactions =
        notes.reactions(row.project(), row.url(), List.copyOf(ours.keySet()));
    if (reactions.facts().isEmpty()) {
      return reactions.failure().isBlank() || warn(row, reactions.failure());
    }
    record(row, ours, reactions.facts().get());
    return true;
  }

  private void record(LedgerRow row, Map<String, RequestNote> ours, Map<String, Reaction> read) {
    Map<String, String> counts = new LinkedHashMap<>(row.counts());
    String brief = NotePoster.digest(panel.brief());
    read.forEach((id, reaction) -> {
      RequestNote note = ours.get(id);
      if (note == null) {
        return;
      }
      String resolution = note.resolution() == Resolution.RESOLVED ? "fixed" : "unknown";
      String now = reaction.likes() + "/" + reaction.dislikes() + "/" + resolution;
      if (!now.equals(counts.get(id))) {
        stats.append(row.url(), id, reaction.likes(), reaction.dislikes(), resolution, brief);
        counts.put(id, now);
      }
    });
    ledger.update(row.url(), row.project(), row.taskId(), standing -> standing.withCounts(counts));
  }

  private static boolean warn(LedgerRow row, String cause) {
    log.atWarn()
        .setMessage("review signals unread")
        .addKeyValue("request", row.url())
        .addKeyValue("cause", cause)
        .addKeyValue("effect", "no line is written and the last counts stand")
        .log();
    return false;
  }
}

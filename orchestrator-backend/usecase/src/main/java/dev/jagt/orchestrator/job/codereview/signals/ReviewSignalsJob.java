package dev.jagt.orchestrator.job.codereview.signals;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.job.codereview.ReviewSubjects;
import dev.jagt.orchestrator.job.codereview.request.LedgerRow;
import dev.jagt.orchestrator.job.codereview.request.NotePoster;
import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
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
 * line only where one changed. A request that is no longer listed, or that the host says is merged
 * or closed, is read once more and ends.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewSignalsJob implements Job {

  private final ReviewLedger ledger;
  private final ReviewSubjects subjects;
  private final RequestNotes notes;
  private final SignalRecorder recorder;

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
      boolean unlisted = !listed.contains(row.url()) && listing.complete(row.project());
      if (read && (unlisted || over(row))) {
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
    recorder.record(row, ours, reactions.facts().get());
    return true;
  }

  private boolean over(LedgerRow row) {
    return notes
        .change(row.project(), row.url())
        .facts()
        .map(change -> change.state() != RequestState.OPEN)
        .orElse(false);
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

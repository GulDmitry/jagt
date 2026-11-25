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
import dev.jagt.orchestrator.task.review.AiLine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads the likes, dislikes and resolutions of the notes the code reviewer posted and of the
 * replies jagt posted under the AI line, and the human replies under them, on every request a task
 * holds or a ledger row names, and records a line only where one changed. A request that is no
 * longer listed, or that the host says is merged or closed, is read once more and ends.
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
    return "record the likes, dislikes and resolutions of the notes and replies jagt posted";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(10);
  }

  @Override
  public void run() {
    ReviewSubjects.Listing listing = subjects.list();
    Set<String> held = ledger.rows().stream().map(LedgerRow::url).collect(Collectors.toSet());
    Set<String> listed =
        listing.subjects().stream().map(subject -> subject.url()).collect(Collectors.toSet());
    for (LedgerRow row : rows(listing)) {
      if (row.ended()) {
        continue;
      }
      boolean read = read(row);
      if (read && !held.contains(row.url())) {
        ledger.update(row.url(), row.project(), row.taskId(), standing -> standing);
      }
      boolean unlisted = !listed.contains(row.url()) && listing.complete(row.project());
      if (read && (unlisted || over(row))) {
        ledger.update(row.url(), row.project(), row.taskId(), standing -> standing.withEnded(true));
      }
    }
  }

  private List<LedgerRow> rows(ReviewSubjects.Listing listing) {
    List<LedgerRow> rows = new ArrayList<>(ledger.rows());
    Set<String> held = rows.stream().map(LedgerRow::url).collect(Collectors.toSet());
    listing.subjects().stream()
        .filter(subject -> !held.contains(subject.url()))
        .filter(subject -> !subject.taskId().isBlank())
        .map(subject -> LedgerRow.of(subject.url(), subject.project(), subject.taskId()))
        .forEach(rows::add);
    return rows;
  }

  private boolean read(LedgerRow row) {
    Answer<List<RequestNote>> read = notes.notes(row.project(), row.url());
    if (read.facts().isEmpty()) {
      return warn(row, read.failure());
    }
    Map<String, RequestNote> ours =
        read.facts().get().stream()
            .filter(note -> note.kind() == AuthorKind.ACTOR)
            .filter(note -> note.body().contains(NotePoster.MARKER) || AiLine.opens(note.body()))
            .collect(
                Collectors.toMap(
                    RequestNote::id, note -> note, (first, later) -> first, LinkedHashMap::new));
    if (ours.isEmpty()) {
      return true;
    }
    Map<String, RequestNote> replies = humanReplies(read.facts().get(), ours.values());
    List<String> rated = new ArrayList<>(ours.keySet());
    rated.addAll(replies.keySet());
    Answer<Map<String, Reaction>> reactions = notes.reactions(row.project(), row.url(), rated);
    if (reactions.facts().isEmpty()) {
      return reactions.failure().isBlank() || warn(row, reactions.failure());
    }
    recorder.record(row, ours, replies, reactions.facts().get());
    return true;
  }

  private static Map<String, RequestNote> humanReplies(
      List<RequestNote> all, Collection<RequestNote> ours) {
    Set<String> threads = ours.stream().map(RequestNote::thread).collect(Collectors.toSet());
    return all.stream()
        .filter(note -> note.kind() == AuthorKind.PERSON)
        .filter(note -> !note.thread().isBlank() && threads.contains(note.thread()))
        .collect(
            Collectors.toMap(
                RequestNote::id, note -> note, (first, later) -> first, LinkedHashMap::new));
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

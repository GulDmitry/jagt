package dev.jagt.orchestrator.job.codereview.signals;

import dev.jagt.orchestrator.job.codereview.CodeReviewPanel;
import dev.jagt.orchestrator.job.codereview.request.LedgerRow;
import dev.jagt.orchestrator.job.codereview.request.NotePoster;
import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.job.codereview.request.ReviewResolutions;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Writes a line for each note or reply whose likes, dislikes or resolution changed since the last
 * read. A note the session answered in {@code review_replies.md} is resolved as that answer, any
 * other as the host says: fixed where its thread is resolved, else unknown. A reply carries no
 * brief digest: the reviewer's brief did not write it. A note's reason is what people replied under
 * it, with their reactions; a thumbs down nobody explained is unknown.
 */
@Component
@RequiredArgsConstructor
class SignalRecorder {

  private static final String UNKNOWN = "unknown";

  private static final int MAX_REASON = 500;

  private final ReviewLedger ledger;
  private final ReviewStats stats;
  private final CodeReviewPanel panel;
  private final ReviewResolutions resolutions;

  void record(
      LedgerRow row,
      Map<String, RequestNote> ours,
      Map<String, RequestNote> replies,
      Map<String, Reaction> read) {
    Map<String, String> counts = new LinkedHashMap<>(row.counts());
    Map<String, String> answered = resolutions.answered(row);
    String noteBrief = NotePoster.digest(panel.brief());
    ours.forEach((id, note) -> {
      Reaction reaction = read.get(id);
      if (reaction == null) {
        return;
      }
      String resolution =
          answered.getOrDefault(
              ReviewResolutions.number(id),
              note.resolution() == Resolution.RESOLVED ? "fixed" : "unknown");
      String reason = reason(note, reaction, replies.values(), read);
      String now = state(reaction, resolution, reason);
      if (!now.equals(counts.get(id))) {
        ReviewStats.Rating rating =
            new ReviewStats.Rating(reaction.likes(), reaction.dislikes(), resolution, reason);
        stats.append(row.url(), id, kind(note), rating, brief(note, noteBrief));
        counts.put(id, now);
      }
    });
    ledger.update(row.url(), row.project(), row.taskId(), standing -> standing.withCounts(counts));
  }

  private static String state(Reaction reaction, String resolution, String reason) {
    String counted = reaction.likes() + "/" + reaction.dislikes() + "/" + resolution;
    return reason.isEmpty() || reason.equals(UNKNOWN)
        ? counted
        : counted + "/" + NotePoster.digest(reason);
  }

  private static String kind(RequestNote note) {
    return note.body().contains(NotePoster.MARKER) ? ReviewStats.NOTE : ReviewStats.REPLY;
  }

  private static String brief(RequestNote note, String noteBrief) {
    return kind(note).equals(ReviewStats.NOTE) ? noteBrief : "";
  }

  private static String reason(
      RequestNote note,
      Reaction reaction,
      Collection<RequestNote> replies,
      Map<String, Reaction> read) {
    String said =
        replies.stream()
            .filter(reply -> !note.thread().isBlank() && reply.thread().equals(note.thread()))
            .map(reply -> said(reply, read.get(reply.id())))
            .collect(Collectors.joining(" | "));
    if (!said.isEmpty()) {
      return said;
    }
    return reaction.dislikes() > 0 ? UNKNOWN : "";
  }

  private static String said(RequestNote reply, @Nullable Reaction reaction) {
    String text = reply.body().strip().replaceAll("\\s+", " ");
    String cut = text.length() > MAX_REASON ? text.substring(0, MAX_REASON) : text;
    return reaction == null
        ? cut
        : cut + " (+" + reaction.likes() + "/-" + reaction.dislikes() + ")";
  }
}

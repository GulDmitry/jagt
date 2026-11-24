package dev.jagt.orchestrator.job.codereview.signals;

import dev.jagt.orchestrator.job.codereview.CodeReviewPanel;
import dev.jagt.orchestrator.job.codereview.request.LedgerRow;
import dev.jagt.orchestrator.job.codereview.request.NotePoster;
import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.job.codereview.request.ReviewResolutions;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Writes a line for each note or reply whose likes, dislikes or resolution changed since the last
 * read. A note the session answered in {@code review_replies.md} is resolved as that answer, any
 * other as the host says: fixed where its thread is resolved, else unknown. A reply carries no
 * brief digest: the reviewer's brief did not write it.
 */
@Component
@RequiredArgsConstructor
class SignalRecorder {

  private final ReviewLedger ledger;
  private final ReviewStats stats;
  private final CodeReviewPanel panel;
  private final ReviewResolutions resolutions;

  void record(LedgerRow row, Map<String, RequestNote> ours, Map<String, Reaction> read) {
    Map<String, String> counts = new LinkedHashMap<>(row.counts());
    Map<String, String> answered = resolutions.answered(row);
    String noteBrief = NotePoster.digest(panel.brief());
    read.forEach((id, reaction) -> {
      RequestNote note = ours.get(id);
      if (note == null) {
        return;
      }
      String resolution =
          answered.getOrDefault(
              ReviewResolutions.number(id),
              note.resolution() == Resolution.RESOLVED ? "fixed" : "unknown");
      String now = reaction.likes() + "/" + reaction.dislikes() + "/" + resolution;
      if (!now.equals(counts.get(id))) {
        String kind =
            note.body().contains(NotePoster.MARKER) ? ReviewStats.NOTE : ReviewStats.REPLY;
        String brief = kind.equals(ReviewStats.NOTE) ? noteBrief : "";
        stats.append(row.url(), id, kind, reaction.likes(), reaction.dislikes(), resolution, brief);
        counts.put(id, now);
      }
    });
    ledger.update(row.url(), row.project(), row.taskId(), standing -> standing.withCounts(counts));
  }
}

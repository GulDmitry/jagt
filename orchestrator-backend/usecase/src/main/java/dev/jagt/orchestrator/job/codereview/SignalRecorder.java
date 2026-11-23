package dev.jagt.orchestrator.job.codereview;

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
 * Writes a line for each note whose likes, dislikes or resolution changed since the last read. A
 * note the session answered in {@code review_replies.md} is resolved as that answer, any other as
 * the host says: fixed where its thread is resolved, else unknown.
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
    String brief = NotePoster.digest(panel.brief());
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
        stats.append(row.url(), id, reaction.likes(), reaction.dislikes(), resolution, brief);
        counts.put(id, now);
      }
    });
    ledger.update(row.url(), row.project(), row.taskId(), standing -> standing.withCounts(counts));
  }
}

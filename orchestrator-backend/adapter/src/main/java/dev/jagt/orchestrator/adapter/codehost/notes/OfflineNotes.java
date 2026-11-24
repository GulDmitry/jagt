package dev.jagt.orchestrator.adapter.codehost.notes;

import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.adapter.codehost.NoCodeHost;
import dev.jagt.orchestrator.adapter.codehost.ReplayCodeHost;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * A code host that is never asked, none or a replayed run: no request awaits, none has a note,
 * nothing is posted.
 */
@Component
public class OfflineNotes implements NotesDriver {

  @Override
  public Set<String> kinds() {
    return Set.of(NoCodeHost.ID, ReplayCodeHost.ID);
  }

  @Override
  public Answer<List<ReviewRequest>> awaitingReview(HostSettings host, String remoteUrl) {
    return new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
  }

  @Override
  public Answer<RequestChange> change(HostSettings host, String requestUrl) {
    return Answer.unavailable();
  }

  @Override
  public Answer<List<RequestNote>> notes(HostSettings host, String requestUrl) {
    return new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
  }

  @Override
  public Answer<String> post(HostSettings host, String requestUrl, NoteDraft draft) {
    return Answer.failed(
        TokenUsage.NONE, host.selectedBy() + " reaches no host: no note is posted");
  }

  @Override
  public Answer<Map<String, Reaction>> reactions(
      HostSettings host, String requestUrl, List<String> noteIds) {
    return Answer.unavailable();
  }
}

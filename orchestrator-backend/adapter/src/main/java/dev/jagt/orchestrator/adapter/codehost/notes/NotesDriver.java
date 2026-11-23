package dev.jagt.orchestrator.adapter.codehost.notes;

import dev.jagt.orchestrator.adapter.codehost.HostNeeds;
import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import dev.jagt.orchestrator.port.host.Problem;
import java.util.List;
import java.util.Map;

/**
 * One kind of code host answering the review port in jagt's own words: a host's wording stops here.
 */
public interface NotesDriver extends HostNeeds {

  Answer<List<ReviewRequest>> awaitingReview(HostSettings host, String remoteUrl);

  Answer<RequestChange> change(HostSettings host, String requestUrl);

  Answer<List<RequestNote>> notes(HostSettings host, String requestUrl);

  Answer<String> post(HostSettings host, String requestUrl, NoteDraft draft);

  Answer<Map<String, Reaction>> reactions(
      HostSettings host, String requestUrl, List<String> noteIds);

  @Override
  default List<Problem> problems(HostSettings host) {
    return List.of();
  }
}

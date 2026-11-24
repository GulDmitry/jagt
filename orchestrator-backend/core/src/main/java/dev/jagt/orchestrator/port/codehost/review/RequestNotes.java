package dev.jagt.orchestrator.port.codehost.review;

import dev.jagt.orchestrator.port.assistant.Answer;
import java.util.List;
import java.util.Map;

/**
 * What jagt reads of a request's review and the one thing it writes there, a note. {@code project}
 * is the key in {@code jagt.yml} whose code host answers. A failure carries the host's or the
 * tool's own words and is never read as an empty list; an empty answer with no failure is a host
 * that has none.
 */
public interface RequestNotes {

  /**
   * The open requests of the repository at {@code remoteUrl} the host names for review by the
   * account its CLI acts as, never one that account opened itself.
   */
  Answer<List<ReviewRequest>> awaitingReview(String project, String remoteUrl);

  /** The request at {@code requestUrl}: its description and its diff. */
  Answer<RequestChange> change(String project, String requestUrl);

  /** Every note on the request, whoever wrote it. */
  Answer<List<RequestNote>> notes(String project, String requestUrl);

  /** The host's id of the note posted on the request at {@code requestUrl}. */
  Answer<String> post(String project, String requestUrl, NoteDraft draft);

  /**
   * The likes and dislikes of each note in {@code noteIds}. An empty answer with no failure is a
   * host that cannot say: it is never read as zero.
   */
  Answer<Map<String, Reaction>> reactions(String project, String requestUrl, List<String> noteIds);
}

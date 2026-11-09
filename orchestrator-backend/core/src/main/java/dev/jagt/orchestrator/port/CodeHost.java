package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.FailedJob;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * jagt's code-host writes, the re-run of a pipeline's failed jobs and the closing of a request, and
 * its reads: why they failed, what ran on a commit or after it, and which notes of a request still
 * await an answer. {@code project} is the key in {@code jagt.yml} whose code host answers.
 */
public interface CodeHost {

  /**
   * The failed jobs of the pipeline at {@code pipelineUrl}, each told apart as infrastructure or
   * code; the failure says what stopped a read.
   */
  Answer<List<FailedJob>> failedJobs(String project, String pipelineUrl);

  /**
   * Re-runs only what failed; the failure carries the host's or the tool's own words for a refusal.
   */
  Answer<String> retryFailed(String project, String pipelineUrl);

  /**
   * Closes the open request at {@code requestUrl} unmerged, where it is on the host the repository
   * at {@code remoteUrl} lives on; the failure carries the host's or the tool's own words for a
   * refusal, or says the address is on no such host.
   */
  Answer<String> closeRequest(String project, @Nullable String remoteUrl, String requestUrl);

  /**
   * The host's id of the newest note of each unresolved thread of the request at {@code requestUrl}
   * whose newest note is not by the account the host's CLI acts as; none unresolved is an empty
   * list, a host that reads no notes the same.
   */
  Answer<List<String>> unresolvedNotes(String project, String requestUrl);

  /**
   * What the host shows of the request at {@code requestUrl} without a model: its state, head
   * commit, checks and reviews, equal between two reads only where none of them changed. Empty
   * facts where the host gives none.
   */
  Answer<String> requestFingerprint(String project, String requestUrl);

  /**
   * The pipeline the host ran for {@code sha} in the repository at {@code remoteUrl}; the worst of
   * several, none read as {@code none}.
   */
  Answer<CommitPipeline> onCommit(String project, String remoteUrl, String sha);

  /**
   * The newest finished pipeline on {@code branch} whose commit has {@code sha} as an ancestor or
   * is it; none read as {@code none}.
   */
  Answer<CommitPipeline> onBranchSince(String project, String remoteUrl, String branch, String sha);
}

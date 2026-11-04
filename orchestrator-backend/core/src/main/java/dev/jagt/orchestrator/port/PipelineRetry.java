package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.FailedJob;

import java.util.List;

/**
 * jagt's one code-host write, the re-run of a pipeline's failed jobs, and its reads: why they failed, and what ran on
 * a commit or after it. {@code project} is the key in {@code jagt.yml} whose code host answers.
 */
public interface PipelineRetry {

    /** The failed jobs of the pipeline at {@code pipelineUrl}, each told apart as infrastructure or code; the failure says what stopped a read. */
    Answer<List<FailedJob>> failedJobs(String project, String pipelineUrl);

    /** Re-runs only what failed; the failure carries the host's or the tool's own words for a refusal. */
    Answer<String> retryFailed(String project, String pipelineUrl);

    /** The pipeline the host ran for {@code sha} in the repository at {@code remoteUrl}; the worst of several, none read as {@code none}. */
    Answer<CommitPipeline> onCommit(String project, String remoteUrl, String sha);

    /** The newest finished pipeline on {@code branch} whose commit has {@code sha} as an ancestor or is it; none read as {@code none}. */
    Answer<CommitPipeline> onBranchSince(String project, String remoteUrl, String branch, String sha);
}

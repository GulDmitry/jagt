package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.FailedJob;

import java.util.List;

/** jagt's one code-host write, the re-run of a pipeline's failed jobs, and its reads: why they failed, and what ran on a commit. */
public interface PipelineRetry {

    /** The failed jobs of the pipeline at {@code pipelineUrl} with the reason the host gives; the failure says what stopped a read. */
    Answer<List<FailedJob>> failedJobs(String pipelineUrl);

    /** Re-runs only what failed; the failure carries the host's or the tool's own words for a refusal. */
    Answer<String> retryFailed(String pipelineUrl);

    /** The pipeline the host ran for {@code sha} in the repository at {@code remoteUrl}; the worst of several, none read as {@code NONE}. */
    Answer<CommitPipeline> onCommit(String remoteUrl, String sha);
}

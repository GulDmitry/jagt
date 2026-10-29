package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.FailedJob;

import java.util.List;

/** jagt's one code-host write: the re-run of a pipeline's failed jobs, and the read of why they failed. */
public interface PipelineRetry {

    /** The failed jobs of the pipeline at {@code pipelineUrl} with the reason the host gives; the failure says what stopped a read. */
    Answer<List<FailedJob>> failedJobs(String pipelineUrl);

    /** Re-runs only what failed; the failure carries the host's or the tool's own words for a refusal. */
    Answer<String> retryFailed(String pipelineUrl);
}

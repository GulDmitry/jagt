package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.task.FailedJob;

import java.util.List;

/** One kind of code host, answering in jagt's own words: a host's wording stops here. */
public interface CodeHostDriver {

    /** The {@code codeHost} value selecting this driver. */
    String id();

    Answer<List<FailedJob>> failedJobs(HostSettings host, String pipelineUrl);

    Answer<String> retryFailed(HostSettings host, String pipelineUrl);

    Answer<String> closeRequest(HostSettings host, String requestUrl);

    /** The newest note's id of each unresolved thread of the request not answered last by the account the host's CLI acts as. */
    Answer<List<String>> unresolvedNotes(HostSettings host, String requestUrl);

    Answer<CommitPipeline> onCommit(HostSettings host, String remoteUrl, String sha);

    Answer<CommitPipeline> onBranchSince(HostSettings host, String remoteUrl, String branch, String sha);

    /** What this driver needs for {@code host} and does not have, without asking the network. */
    List<Problem> problems(HostSettings host);
}

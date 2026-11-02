package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.TokenUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** No pipeline is read, so a deploy settles on the address no host has. */
@Component
@ConditionalOnExpression("'${orchestrator.agent.cli:claude}' != 'replay'")
public class NoCodeHost implements CodeHostDriver {

    static final String ID = "none";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Answer<List<FailedJob>> failedJobs(HostSettings host, String pipelineUrl) {
        return new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
    }

    @Override
    public Answer<String> retryFailed(HostSettings host, String pipelineUrl) {
        return Answer.failed(TokenUsage.NONE, host.selectedBy() + " is none: nothing is re-run");
    }

    @Override
    public Answer<CommitPipeline> onCommit(HostSettings host, String remoteUrl, String sha) {
        return new Answer<>(Optional.of(CommitPipeline.unhosted()), TokenUsage.NONE);
    }

    @Override
    public List<Problem> problems(HostSettings host) {
        return List.of();
    }
}

package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.TokenUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** A replayed run reaches no host: every failure reads as the code's and nothing is re-run. */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
public class ReplayPipelineRetry implements PipelineRetry {

    @Override
    public Answer<List<FailedJob>> failedJobs(String pipelineUrl) {
        return new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
    }

    @Override
    public Answer<String> retryFailed(String pipelineUrl) {
        return Answer.failed(TokenUsage.NONE, "a replayed run sends nothing to a host");
    }
}

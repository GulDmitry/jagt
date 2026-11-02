package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.task.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** A replayed run reaches no host: every failure reads as the code's, every commit as green, and nothing is re-run. */
@Component
public class ReplayCodeHost extends OfflineCodeHost {

    static final String ID = "replay";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Answer<String> retryFailed(HostSettings host, String pipelineUrl) {
        return Answer.failed(TokenUsage.NONE, "a replayed run sends nothing to a host");
    }

    @Override
    public Answer<CommitPipeline> onCommit(HostSettings host, String remoteUrl, String sha) {
        return new Answer<>(Optional.of(new CommitPipeline("success", "")), TokenUsage.NONE);
    }
}

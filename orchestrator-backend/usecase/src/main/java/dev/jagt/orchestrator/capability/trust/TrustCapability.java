package dev.jagt.orchestrator.capability.trust;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.service.TrustedGitState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrustCapability implements TaskCapability {

    private final TrustedGitState trusted;

    @Override
    public TaskAction action() {
        return TaskAction.TRUST;
    }

    @Override
    public Outcome run(String taskId) {
        trusted.trust(taskId);
        return Outcome.ok("trust " + taskId + ": its git config and hooks are trusted as they are now");
    }
}

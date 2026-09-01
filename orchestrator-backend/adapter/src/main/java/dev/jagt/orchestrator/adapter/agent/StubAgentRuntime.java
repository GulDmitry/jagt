package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.AgentWorktree;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Writes NO agent config: a scripted agent POSTs to {@code /mcp} itself. */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "stub")
@RequiredArgsConstructor
/** The one non-deterministic participant an end-to-end assertion has to replace. */
public class StubAgentRuntime extends AbstractAgentRuntime {

    private final StubAgentProperties stub;

    @Override
    public String displayName() {
        return "Stub";
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode) {
        if (stub.script() == null) {
            // Exits immediately and successfully: a hanging placeholder would wait out its own timeout.
            return "true";
        }
        return shellQuote(stub.script()) + " " + shellQuote(worktree.toString()) + (planMode ? " plan" : "");
    }

    @Override
    protected void wireAgent(AgentWorktree worktree) {
        // Deliberately nothing.
    }
}

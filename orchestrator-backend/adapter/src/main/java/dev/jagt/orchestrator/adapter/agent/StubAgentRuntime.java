package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.config.StubAgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * A scripted agent for automated runs ({@code orchestrator.agent=stub}), which is the ONE non-deterministic
 * participant an end-to-end assertion has to replace. Everything else in such a run stays real.
 *
 * <p>It writes NO agent config: a scripted agent POSTs to {@code /mcp} itself. That absence is also the
 * assertion — a Claude-shaped file in a stub worktree means something outside the runtime put it there.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent", havingValue = "stub")
@RequiredArgsConstructor
public class StubAgentRuntime extends AbstractAgentRuntime {

    private final StubAgentProperties stub;

    @Override
    public String displayName() {
        return "Stub";
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode) {
        if (stub.script() == null) {
            // Exits immediately and successfully: a lifecycle assertion needs a session that STARTED, and a
            // hanging placeholder would make every matrix combination wait for its own timeout.
            return "true";
        }
        return shellQuote(stub.script()) + " " + shellQuote(worktree.toString()) + (planMode ? " plan" : "");
    }

    @Override
    protected void wireAgent(AgentWorktree worktree) {
        // Deliberately nothing — see the class javadoc.
    }
}

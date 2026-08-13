package dev.jagt.orchestrator.agent;

import dev.jagt.orchestrator.config.StubAgentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * A scripted agent for automated runs — selected by {@code orchestrator.agent=stub}, never by a human working
 * on real tickets. It exists because the whole task flow (create worktree → provision → launch → talk over
 * MCP → ship/review/deploy/done) can only be asserted end-to-end if the ONE non-deterministic participant, the
 * model, is replaced by something that behaves the same way twice. Everything else in an e2e run stays real:
 * real git worktrees, real tmux windows, real state transitions.
 *
 * <p>It writes NO agent config into the worktree: a scripted agent talks to the orchestrator over
 * {@code POST /mcp} directly (the {@code X-Working-Directory} header is the scoping), so the only artifact it
 * needs is the proxy the template links for every runtime. That absence is also the assertion that
 * per-agent provisioning really lives behind this seam — if a Claude-shaped file shows up in a stub worktree,
 * something outside the runtime put it there.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent", havingValue = "stub")
public class StubAgentRuntime extends AbstractAgentRuntime {

    private final StubAgentProperties stub;

    public StubAgentRuntime(StubAgentProperties stub) {
        this.stub = stub;
    }

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

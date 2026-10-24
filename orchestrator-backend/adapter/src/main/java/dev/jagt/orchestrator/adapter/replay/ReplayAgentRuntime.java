package dev.jagt.orchestrator.adapter.replay;

import dev.jagt.orchestrator.adapter.agent.AbstractAgentRuntime;
import dev.jagt.orchestrator.port.AgentWorktree;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A session that is a recorded run played back: its window idles, its moves come from {@link ReplaySessions}. */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
@RequiredArgsConstructor
public class ReplayAgentRuntime extends AbstractAgentRuntime {

    private final ReplaySessions sessions;

    @Override
    public String displayName() {
        return "Replay";
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode) {
        sessions.launched(worktree);
        return "exec cat >/dev/null";
    }

    @Override
    public Optional<List<String>> humanSaid(Path worktree, Set<String> typedByJagt) {
        return Optional.of(List.of());
    }

    @Override
    public boolean readsWhatTheHumanTyped() {
        return true;
    }

    @Override
    protected void wireAgent(AgentWorktree worktree) {
        // A replayed session reaches jagt over HTTP itself and reads no config.
    }
}

package dev.jagt.orchestrator.adapter.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReplayAgentRuntimeTest {

    @Test
    void aReplayedSessionIsNamedReplay() {
        ReplayAgentRuntime runtime = new ReplayAgentRuntime(mock(ReplaySessions.class));

        assertThat(runtime.displayName()).isEqualTo("Replay");
    }

    @Test
    void aReplayedSessionsWindowIdlesAndTellsTheReplayItLaunched() {
        ReplaySessions sessions = mock(ReplaySessions.class);
        ReplayAgentRuntime runtime = new ReplayAgentRuntime(sessions);
        Path worktree = Path.of("/work/ABC-1");

        String command = runtime.launchCommand(worktree, false);

        assertThat(command).isEqualTo("exec cat >/dev/null");
        verify(sessions).launched(worktree);
    }

    @Test
    void aReplayedSessionHasNothingTheHumanTypedYetReadsWhatTheyType() {
        ReplayAgentRuntime runtime = new ReplayAgentRuntime(mock(ReplaySessions.class));

        assertThat(runtime.humanSaid(Path.of("/work/ABC-1"), Set.of())).contains(java.util.List.of());
        assertThat(runtime.readsWhatTheHumanTyped()).isTrue();
    }
}

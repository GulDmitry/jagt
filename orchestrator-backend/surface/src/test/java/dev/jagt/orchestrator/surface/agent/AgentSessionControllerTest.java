package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.WatchdogService;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSessionControllerTest {

    @Test
    void recordsTheReportAgainstTheTaskRunningInThatWorktree() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));
        SessionProbe probe = mock(SessionProbe.class);

        new AgentSessionController(state, probe, mock(WatchdogService.class))
                .report("waiting", "/wt/ABC-1-proj", null);

        verify(probe).report(eq("ABC-1"), eq(SessionProbe.State.WAITING), anyLong());
    }

    /** Waiting out the sweep would leave the board claiming the session is working for a whole interval. */
    @Test
    void hasTheTaskJudgedAtOnceRatherThanOnTheNextSweep() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));
        WatchdogService watchdog = mock(WatchdogService.class);

        new AgentSessionController(state, mock(SessionProbe.class), watchdog)
                .report("working", "/wt/ABC-1-proj", null);

        verify(watchdog).check("ABC-1");
    }

    /** Deriving where a session writes its log is a guess; the session itself naming the file is not. */
    @Test
    void believesTheLogFileThePayloadNames() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));
        SessionProbe probe = mock(SessionProbe.class);

        new AgentSessionController(state, probe, mock(WatchdogService.class))
                .report("waiting", "/wt/ABC-1-proj",
                        new AgentSessionController.Session("/logs/session.jsonl"));

        verify(probe).logAt("ABC-1", Path.of("/logs/session.jsonl"));
    }

    @Test
    void refusesAReportFromADirectoryNoTaskRunsIn() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AgentSessionController(state, mock(SessionProbe.class),
                mock(WatchdogService.class)).report("waiting", "/elsewhere", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/elsewhere");
    }

    /** A mistyped hook would otherwise be answered with a 200 and report nothing for the life of the install. */
    @Test
    void refusesAStateThatIsNotOneOfJagtsOwn() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        assertThatThrownBy(() -> new AgentSessionController(state, mock(SessionProbe.class),
                mock(WatchdogService.class)).report("busy", "/wt/ABC-1-proj", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("busy");
    }
}

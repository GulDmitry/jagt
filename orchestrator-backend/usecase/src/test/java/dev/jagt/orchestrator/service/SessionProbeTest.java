package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionProbeTest {

    @Test
    void readsTheLogFileASessionNamedForItself(@TempDir Path root) throws Exception {
        Path log = Files.writeString(root.resolve("session.jsonl"), "{}");
        Files.setLastModifiedTime(log, FileTime.fromMillis(9_990_000L));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), running(), mock(AgentRuntime.class));

        probe.logAt("ABC-1", log);

        assertThat(probe.read("ABC-1", task, 300_000, 10_000_000).logAt()).isEqualTo(9_990_000L);
    }

    @Test
    void doesNotLetTheFileASessionOnceNamedOutrankAFresherOne(@TempDir Path root) throws Exception {
        Path stale = Files.writeString(root.resolve("earlier.jsonl"), "{}");
        Files.setLastModifiedTime(stale, FileTime.fromMillis(9_000_000L));
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.lastSessionActivity(any())).thenReturn(OptionalLong.of(9_990_000L));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), running(), runtime);
        probe.logAt("ABC-1", stale);

        assertThat(probe.read("ABC-1", task, 300_000, 10_000_000).logAt()).isEqualTo(9_990_000L);
    }

    @Test
    void forgetsTheFileANamedByATaskThatIsNoLongerThere(@TempDir Path root) throws Exception {
        Path log = Files.writeString(root.resolve("session.jsonl"), "{}");
        Files.setLastModifiedTime(log, FileTime.fromMillis(9_990_000L));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_900_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), running(), mock(AgentRuntime.class));
        probe.logAt("ABC-1", log);

        probe.keepOnly(List.of("ABC-2"));

        assertThat(probe.read("ABC-1", task, 300_000, 10_000_000).logAt()).isZero();
    }

    private static AgentSessions running() {
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.windowState(anyString())).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        return sessions;
    }

    @Test
    void asksNoPrintWhileTheFreeSignsAreFresh() {
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.windowState(anyString())).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_900_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), sessions, mock(AgentRuntime.class));

        SessionSign.Read read = probe.read("ABC-1", task, 300_000, 10_000_000);

        assertThat(read.window()).isEqualTo(Session.Window.State.RUNNING);
        verify(sessions, never()).printedAt(anyString());
    }

    @Test
    void takesTerminalOutputIntoTheReadOnceEverythingCheaperReadsStale() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.windowState(anyString())).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(sessions.printedAt(anyString())).thenReturn(9_990_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_700_000L).build();

        SessionSign.Read read = new SessionProbe(config, sessions, mock(AgentRuntime.class))
                .read("ABC-1", task, 300_000, 10_000_000);

        assertThat(read).isEqualTo(new SessionSign.Read(Session.Window.State.RUNNING, 9_990_000L, 0, 9_700_000L, 300_000, null));
    }

    @Test
    void asksNoPrintWhileAFailureStands() {
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.windowState(anyString())).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(1_000L)
                .session(Session.defaults().withHalt(new Session.Halt(Session.Halt.Kind.FAILED, 9_990_000L, null))).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), sessions, mock(AgentRuntime.class));

        probe.read("ABC-1", task, 300_000, 10_000_000);

        verify(sessions, never()).printedAt(anyString());
    }

    @Test
    void keepsAnIntervalWhenTheConfigCannotBeReadAtAll() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenThrow(new IllegalStateException("config.json is half-written"));

        Duration every = new SessionProbe(config, running(), mock(AgentRuntime.class)).every();

        assertThat(every).isEqualTo(Duration.ofSeconds(600));
    }

    @Test
    void stillReadsWhileTheConfigFileCannotBeRead() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenThrow(new IllegalStateException("config.json is half-written"));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_900_000L).build();

        SessionSign.Read read = new SessionProbe(config, running(), mock(AgentRuntime.class))
                .read("ABC-1", task, 300_000, 10_000_000);

        assertThat(read.staleAfter()).isEqualTo(300_000);
    }

    @Test
    void readsNothingFromAWindowListThatCannotBeRead() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.windowState(anyString())).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(sessions.windowState(anyString())).thenThrow(new SessionHost.WindowsUnreadable(
                "tmux list-windows jagt failed (exit 1): server timed out"));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(config, sessions, mock(AgentRuntime.class));

        assertThatThrownBy(() -> probe.read("ABC-1", task, 300_000, 10_000_000))
                .isInstanceOf(SessionHost.WindowsUnreadable.class);
    }

    @Test
    void readsAWindowHoldingNoAgentAsDeadWithWhatTheAgentPrintedOnItsWayOut() {
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.windowState("ABC-1")).thenReturn(SessionHost.WindowState.DEAD_SHELL);
        when(sessions.exitTail("ABC-1")).thenReturn(Optional.of("Error: bad config"));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_900_000L).build();

        SessionSign.Read read = new SessionProbe(mock(ConfigService.class), sessions, mock(AgentRuntime.class))
                .read("ABC-1", task, 300_000, 10_000_000);

        assertThat(read).isEqualTo(new SessionSign.Read(Session.Window.State.DEAD_SHELL, 0, 0, 9_900_000L, 300_000,
                "Error: bad config"));
    }

    @Test
    void readsAMissingWindowAsMissingWithNoWords() {
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.windowState("ABC-1")).thenReturn(SessionHost.WindowState.MISSING);
        when(sessions.exitTail("ABC-1")).thenReturn(Optional.empty());
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_900_000L).build();

        SessionSign.Read read = new SessionProbe(mock(ConfigService.class), sessions, mock(AgentRuntime.class))
                .read("ABC-1", task, 300_000, 10_000_000);

        assertThat(read.window()).isEqualTo(Session.Window.State.MISSING);
        assertThat(read.exitTail()).isNull();
    }
}

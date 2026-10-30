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
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionProbeTest {

    @Test
    void readsTheLogFileASessionNamedForItself(@TempDir Path root) throws Exception {
        Path log = Files.writeString(root.resolve("session.jsonl"), "{}");
        Files.setLastModifiedTime(log, FileTime.fromMillis(9_990_000L));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), mock(AgentRuntime.class));

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
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), runtime);
        probe.logAt("ABC-1", stale);

        assertThat(probe.read("ABC-1", task, 300_000, 10_000_000).logAt()).isEqualTo(9_990_000L);
    }

    @Test
    void forgetsTheFileANamedByATaskThatIsNoLongerThere(@TempDir Path root) throws Exception {
        Path log = Files.writeString(root.resolve("session.jsonl"), "{}");
        Files.setLastModifiedTime(log, FileTime.fromMillis(9_990_000L));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_900_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), mock(AgentRuntime.class));
        probe.logAt("ABC-1", log);

        probe.keepOnly(List.of("ABC-2"));

        assertThat(probe.read("ABC-1", task, 300_000, 10_000_000).logAt()).isZero();
    }

    @Test
    void asksNoWindowWhileTheFreeSignsAreFresh() {
        SessionHost sessions = mock(SessionHost.class);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_900_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), sessions, mock(AgentRuntime.class));

        SessionSign.Read read = probe.read("ABC-1", task, 300_000, 10_000_000);

        assertThat(read.window()).isEqualTo(Session.Window.State.UNREAD);
        verifyNoInteractions(sessions);
    }

    @Test
    void takesTerminalOutputIntoTheReadOnceEverythingCheaperReadsStale() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        SessionHost sessions = mock(SessionHost.class);
        when(sessions.lastWindowActivityMillis(any(), anyString())).thenReturn(9_990_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_700_000L).build();

        SessionSign.Read read = new SessionProbe(config, sessions, mock(AgentRuntime.class))
                .read("ABC-1", task, 300_000, 10_000_000);

        assertThat(read).isEqualTo(new SessionSign.Read(Session.Window.State.RUNNING, 9_990_000L, 0, 9_700_000L, 300_000));
    }

    @Test
    void asksNoWindowWhileAFailureStands() {
        SessionHost sessions = mock(SessionHost.class);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(1_000L)
                .session(Session.defaults().withHalt(new Session.Halt(Session.Halt.Kind.FAILED, 9_990_000L, null))).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), sessions, mock(AgentRuntime.class));

        probe.read("ABC-1", task, 300_000, 10_000_000);

        verifyNoInteractions(sessions);
    }

    @Test
    void keepsAnIntervalWhenTheConfigCannotBeReadAtAll() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenThrow(new IllegalStateException("config.json is half-written"));

        Duration every = new SessionProbe(config, mock(SessionHost.class), mock(AgentRuntime.class)).every();

        assertThat(every).isEqualTo(Duration.ofSeconds(600));
    }

    @Test
    void stillReadsWhileTheConfigFileCannotBeRead() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenThrow(new IllegalStateException("config.json is half-written"));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_900_000L).build();

        SessionSign.Read read = new SessionProbe(config, mock(SessionHost.class), mock(AgentRuntime.class))
                .read("ABC-1", task, 300_000, 10_000_000);

        assertThat(read.staleAfter()).isEqualTo(300_000);
    }

    @Test
    void readsNothingFromAWindowListThatCannotBeRead() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        SessionHost sessions = mock(SessionHost.class);
        when(sessions.lastWindowActivityMillis(any(), anyString())).thenThrow(new SessionHost.WindowsUnreadable(
                "tmux list-windows jagt failed (exit 1): server timed out"));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(config, sessions, mock(AgentRuntime.class));

        assertThatThrownBy(() -> probe.read("ABC-1", task, 300_000, 10_000_000))
                .isInstanceOf(SessionHost.WindowsUnreadable.class);
    }
}

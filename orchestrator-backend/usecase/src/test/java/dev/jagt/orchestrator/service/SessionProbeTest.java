package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionProbeTest {

    @Test
    void raisesAHarnessReportEvenWhileTheTerminalIsStillPrinting() {
        SessionHost sessions = mock(SessionHost.class);
        when(sessions.lastWindowActivityMillis(any(), anyString())).thenReturn(10_000_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_990_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), sessions, mock(AgentRuntime.class));

        probe.report("ABC-1", SessionProbe.State.WAITING, 10_000_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000))
                .map(SessionProbe.Silence::detail).contains("waiting for input");
    }

    @Test
    void staysQuietForATurnThatJustEndedBecauseTheSessionMayComeStraightBack() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class),
                mock(AgentRuntime.class));

        probe.report("ABC-1", SessionProbe.State.IDLE, 10_000_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000)).isEmpty();
    }

    @Test
    void callsATurnThatEndedSilentOnceNothingHasMovedForTheWholeWindow() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        SessionHost sessions = mock(SessionHost.class);
        when(sessions.lastWindowActivityMillis(any(), anyString())).thenReturn(0L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(config, sessions, mock(AgentRuntime.class));

        probe.report("ABC-1", SessionProbe.State.IDLE, 9_600_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000))
                .map(SessionProbe.Silence::detail).contains("its turn ended and nothing has moved since");
    }

    @Test
    void saysNothingAboutTheTurnWhenTheWindowItselfPrintedSomethingAfterIt() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        SessionHost sessions = mock(SessionHost.class);
        when(sessions.lastWindowActivityMillis(any(), anyString())).thenReturn(9_500_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(config, sessions, mock(AgentRuntime.class));

        probe.report("ABC-1", SessionProbe.State.IDLE, 9_000_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000))
                .get().extracting(SessionProbe.Silence::detail).isNull();
    }

    @Test
    void staysQuietWhileTheSessionsOwnLogIsStillGrowing() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.lastSessionActivity(any())).thenReturn(OptionalLong.of(9_990_000L));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();

        Optional<SessionProbe.Silence> silence =
                new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), runtime)
                        .of("ABC-1", task, 300_000, 10_000_000);

        assertThat(silence).isEmpty();
    }

    @Test
    void takesTerminalOutputAsTheLastSignWhenEverythingCheaperReadsStale() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        SessionHost sessions = mock(SessionHost.class);
        when(sessions.lastWindowActivityMillis(any(), anyString())).thenReturn(9_990_000L);
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.lastSessionActivity(any())).thenReturn(OptionalLong.of(9_000_000L));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();

        Optional<SessionProbe.Silence> silence = new SessionProbe(config, sessions, runtime)
                .of("ABC-1", task, 300_000, 10_000_000);

        assertThat(silence).isEmpty();
    }

    @Test
    void raisesAHarnessReportAtOnceRatherThanWaitingOutTheThreshold() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_990_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class),
                mock(AgentRuntime.class));

        probe.report("ABC-1", SessionProbe.State.GONE, 10_000_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000))
                .map(SessionProbe.Silence::detail).contains("the session ended");
    }

    @Test
    void dropsAWaitingReportTheMomentTheSessionReportsItIsWorkingAgain() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class),
                mock(AgentRuntime.class));
        probe.report("ABC-1", SessionProbe.State.WAITING, 9_500_000);

        probe.report("ABC-1", SessionProbe.State.WORKING, 10_000_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000)).isEmpty();
    }

    @Test
    void dropsAHarnessReportOnceTheSessionsLogHasGrownAgain() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.lastSessionActivity(any())).thenReturn(OptionalLong.of(10_000_000L));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), runtime);

        probe.report("ABC-1", SessionProbe.State.WAITING, 9_500_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000)).isEmpty();
    }

    @Test
    void readsTheLogFileASessionNamedForItself(@TempDir Path root) throws Exception {
        Path log = Files.writeString(root.resolve("session.jsonl"), "{}");
        Files.setLastModifiedTime(log, FileTime.fromMillis(9_990_000L));
        AgentRuntime runtime = mock(AgentRuntime.class);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), runtime);

        probe.logAt("ABC-1", log);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000)).isEmpty();
    }

    @Test
    void believesTheSessionOverTheEndThatWasReportedInTheSameMillisecond() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class),
                mock(AgentRuntime.class));
        probe.report("ABC-1", SessionProbe.State.GONE, 10_000_000);

        probe.report("ABC-1", SessionProbe.State.WORKING, 10_000_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000)).isEmpty();
    }

    @Test
    void doesNotLetTheFileASessionOnceNamedOutrankAFresherOne(@TempDir Path root) throws Exception {
        Path stale = Files.writeString(root.resolve("earlier.jsonl"), "{}");
        Files.setLastModifiedTime(stale, FileTime.fromMillis(9_000_000L));
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.lastSessionActivity(any())).thenReturn(OptionalLong.of(9_990_000L));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), runtime);

        probe.logAt("ABC-1", stale);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000)).isEmpty();
    }

    @Test
    void forgetsWhatItKnewAboutATaskThatIsNoLongerThere() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_990_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class),
                mock(AgentRuntime.class));
        probe.report("ABC-1", SessionProbe.State.WAITING, 10_000_000);

        probe.keepOnly(List.of("ABC-2"));

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000)).isEmpty();
    }

    @Test
    void keepsAnIntervalWhenTheConfigCannotBeReadAtAll() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenThrow(new IllegalStateException("config.json is half-written"));

        Duration every = new SessionProbe(config, mock(SessionHost.class), mock(AgentRuntime.class)).every();

        assertThat(every).isEqualTo(Duration.ofSeconds(600));
    }

    @Test
    void keepsAReportThatTiedWithTheLogEntryWrittenJustBeforeIt() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.lastSessionActivity(any())).thenReturn(OptionalLong.of(10_000_000L));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), runtime);

        probe.report("ABC-1", SessionProbe.State.WAITING, 10_000_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000))
                .map(SessionProbe.Silence::detail).contains("waiting for input");
    }

    @Test
    void stillReachesAVerdictWhileTheConfigFileCannotBeRead() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenThrow(new IllegalStateException("config.json is half-written"));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();

        Optional<SessionProbe.Silence> silence =
                new SessionProbe(config, mock(SessionHost.class), mock(AgentRuntime.class))
                        .of("ABC-1", task, 300_000, 10_000_000);

        assertThat(silence).map(SessionProbe.Silence::since).contains(9_000_000L);
    }
}

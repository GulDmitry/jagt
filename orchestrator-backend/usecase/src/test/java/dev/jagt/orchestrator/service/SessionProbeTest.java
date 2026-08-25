package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * The one that used to be missed: a session waiting at a prompt keeps repainting, so terminal output reads
     * warm forever and no threshold will ever flag it.
     */
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

    /**
     * The one that used to be wrong: a session ends a turn every time it answers, and comes straight back when
     * what it left running finishes — so a turn ending is not a human being waited for.
     */
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
        when(runtime.lastSessionActivityMillis(any())).thenReturn(9_990_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();

        Optional<SessionProbe.Silence> silence =
                new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), runtime)
                        .of("ABC-1", task, 300_000, 10_000_000);

        assertThat(silence).isEmpty();
    }

    /** A log gets its entry when a tool call is issued and nothing while it runs, so a long build looks dead. */
    @Test
    void takesTerminalOutputAsTheLastSignWhenEverythingCheaperReadsStale() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        SessionHost sessions = mock(SessionHost.class);
        when(sessions.lastWindowActivityMillis(any(), anyString())).thenReturn(9_990_000L);
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.lastSessionActivityMillis(any())).thenReturn(9_000_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();

        Optional<SessionProbe.Silence> silence = new SessionProbe(config, sessions, runtime)
                .of("ABC-1", task, 300_000, 10_000_000);

        assertThat(silence).isEmpty();
    }

    /** A report is what happened rather than an absence of it, so no threshold applies. */
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
        when(runtime.lastSessionActivityMillis(any())).thenReturn(10_000_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), runtime);

        probe.report("ABC-1", SessionProbe.State.WAITING, 9_500_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000)).isEmpty();
    }

    /** A session that names the file it appends to is believed over the one jagt would have derived. */
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

    /** Both hooks of one clear are stamped on arrival, so the pair can share a millisecond or invert. */
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

    /** A cleared session keeps writing to a NEW file, and the one it named stops moving without going away. */
    @Test
    void doesNotLetTheFileASessionOnceNamedOutrankAFresherOne(@TempDir Path root) throws Exception {
        Path stale = Files.writeString(root.resolve("earlier.jsonl"), "{}");
        Files.setLastModifiedTime(stale, FileTime.fromMillis(9_000_000L));
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.lastSessionActivityMillis(any())).thenReturn(9_990_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), runtime);

        probe.logAt("ABC-1", stale);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000)).isEmpty();
    }

    /** Ticket keys get reused, so a fresh task must not inherit what was known about the one before it. */
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

    /**
     * Asked on the scheduler's own thread and outside its guard, so a throw here strands the job rather than
     * skipping one run — and a hand-edited file is unreadable for exactly as long as it takes to save.
     */
    @Test
    void keepsAnIntervalWhenTheConfigCannotBeReadAtAll() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenThrow(new IllegalStateException("config.json is half-written"));

        Duration every = new SessionProbe(config, mock(SessionHost.class), mock(AgentRuntime.class)).every();

        assertThat(every).isEqualTo(Duration.ofSeconds(600));
    }

    /**
     * A finished turn is appended to the log immediately before the hook that reports it fires, and both are
     * stamped in whichever millisecond they land in — so a tie must keep the report, not drop it.
     */
    @Test
    void keepsAReportThatTiedWithTheLogEntryWrittenJustBeforeIt() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.lastSessionActivityMillis(any())).thenReturn(10_000_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(9_000_000L).build();
        SessionProbe probe = new SessionProbe(mock(ConfigService.class), mock(SessionHost.class), runtime);

        probe.report("ABC-1", SessionProbe.State.WAITING, 10_000_000);

        assertThat(probe.of("ABC-1", task, 300_000, 10_000_000))
                .map(SessionProbe.Silence::detail).contains("waiting for input");
    }

    /** Thrown here it would cost the whole pass its stamps, and a hook the answer it cannot see anyway. */
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

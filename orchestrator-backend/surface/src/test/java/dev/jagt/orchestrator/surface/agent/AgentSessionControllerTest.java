package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.protocol.SessionHookReport;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.SessionWakes;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSessionControllerTest {

    private final SessionWakes wakes = mock(SessionWakes.class);

    @Test
    void recordsTheReportAgainstTheTaskRunningInThatWorktree() {
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports, wakes).report("waiting", "/wt/ABC-1-proj",
                new SessionHookReport("/logs/session.jsonl", "compact", null));

        verify(reports).record("ABC-1", SessionProbe.State.WAITING,
                SessionReports.Report.defaults().withSessionLog(Path.of("/logs/session.jsonl"))
                        .withStartedBy("compact").withTask(task));
    }

    @Test
    void armsTheWakeForATurnFailureThatIsAUsageLimit() {
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);
        SessionReports.Report report = SessionReports.Report.defaults().withFailedOn("rate_limit").withTask(task);
        when(reports.turnFailed("ABC-1", report)).thenReturn(true);

        new AgentSessionController(state, reports, wakes).turnFailure("/wt/ABC-1-proj",
                new SessionHookReport(null, null, null, null, "rate_limit", null, java.util.List.of(), null));

        verify(wakes).arm("ABC-1");
    }

    @Test
    void armsNoWakeForATurnFailureThatIsNotAUsageLimit() {
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports, wakes).turnFailure("/wt/ABC-1-proj",
                new SessionHookReport(null, null, null, null, "overloaded", null, java.util.List.of(), null));

        verify(wakes, never()).arm("ABC-1");
    }

    @Test
    void cancelsTheWakeWhenTheSessionReportsWorking() {
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports, wakes).report("working", "/wt/ABC-1-proj", null);

        verify(wakes).cancel("ABC-1");
    }

    @Test
    void keepsTheWakeWhenTheSessionReportsIdle() {
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports, wakes).report("idle", "/wt/ABC-1-proj", null);

        verify(wakes, never()).cancel("ABC-1");
    }

    @Test
    void namesNoLogWhenThePayloadCarriedNone() {
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports, wakes).report("working", "/wt/ABC-1-proj", null);

        verify(reports).record("ABC-1", SessionProbe.State.WORKING,
                SessionReports.Report.defaults().withTask(task));
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void dropsATranscriptPathItCannotBelieveAndSaysSoOnceInTheLog() {
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger controllerLog = (Logger) LoggerFactory.getLogger(AgentSessionController.class);
        controllerLog.addAppender(log);
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports, wakes).report("working", "/wt/ABC-1-proj",
                new SessionHookReport("/logs/session.jsonl", "compact", null));
        int warnedForAbsolute = log.list.size();
        new AgentSessionController(state, reports, wakes).report("working", "/wt/ABC-1-proj",
                new SessionHookReport("relative/session.jsonl", "compact", null));
        controllerLog.detachAppender(log);

        verify(reports).record("ABC-1", SessionProbe.State.WORKING,
                SessionReports.Report.defaults().withStartedBy("compact").withTask(task));
        assertThat(warnedForAbsolute).isZero();
        assertThat(List.copyOf(log.list)).filteredOn(event -> "session hook field dropped".equals(event.getMessage()))
                .extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
    }

    @Test
    void answersTheSessionWithWhateverTheReportProduced() {
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);
        when(reports.record("ABC-1", SessionProbe.State.WORKING,
                SessionReports.Report.defaults().withStartedBy("compact").withTask(task)))
                .thenReturn("re-read task_context.md");

        String answered = new AgentSessionController(state, reports, wakes).report("working", "/wt/ABC-1-proj",
                new SessionHookReport(null, "compact", null));

        assertThat(answered).isEqualTo("re-read task_context.md");
    }

    @Test
    void carriesWhatTheCliToldTheHumanSoOneEventCanMeanTwoDifferentWaits() {
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports, wakes).report("idle", "/wt/ABC-1-proj",
                new SessionHookReport(null, null, "Claude needs your permission to use Bash"));

        verify(reports).record("ABC-1", SessionProbe.State.IDLE,
                SessionReports.Report.defaults().withSaid("Claude needs your permission to use Bash").withTask(task));
    }

    @Test
    void tellsTheTurnEndWhetherARefusalAlreadySentItOn() {
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);

        new AgentSessionController(state, reports, wakes).turnEnd("/wt/ABC-1-proj",
                new SessionHookReport(null, null, null, true, null, null, java.util.List.of(Map.of("id", "b1")), null));

        verify(reports).turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), true, true);
    }

    @Test
    void answersTheTurnEndWithTheVerdictOfTheReports() {
        StateService state = mock(StateService.class);
        TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        SessionReports reports = mock(SessionReports.class);
        when(reports.turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), false, false))
                .thenReturn("{\"decision\": \"block\"}");

        String answered = new AgentSessionController(state, reports, wakes).turnEnd("/wt/ABC-1-proj", null);

        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
    }

    @Test
    void refusesAReportFromADirectoryNoTaskRunsIn() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AgentSessionController(state, mock(SessionReports.class), wakes)
                .report("waiting", "/elsewhere", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/elsewhere");
    }

    @Test
    void refusesAStateThatIsNotOneOfJagtsOwn() {
        StateService state = mock(StateService.class);
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        assertThatThrownBy(() -> new AgentSessionController(state, mock(SessionReports.class), wakes)
                .report("busy", "/wt/ABC-1-proj", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("busy");
    }
}

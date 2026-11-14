package dev.jagt.orchestrator.surface.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.protocol.SessionHookReport;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.UsageLimitWakes;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentSessionControllerTest {

  private final UsageLimitWakes wakes = mock(UsageLimitWakes.class);
  private final StateService state = mock(StateService.class);
  private final SessionReports reports = mock(SessionReports.class);
  private final TaskState task =
      TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
  private final AgentSessionController controller =
      new AgentSessionController(state, reports, wakes);

  @Test
  void recordsTheReportAgainstTheTaskRunningInThatWorktree() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));

    controller.report(
        "idle", "/wt/ABC-1-proj", new SessionHookReport("/logs/session.jsonl", "compact", null));

    verify(reports)
        .record(
            "ABC-1",
            SessionProbe.State.IDLE,
            SessionReports.Report.defaults()
                .withSessionLog(Path.of("/logs/session.jsonl"))
                .withStartedBy("compact")
                .withTask(task));
  }

  @Test
  void armsTheWakeForTurnFailureThatIsUsageLimit() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
    SessionReports.Report report =
        SessionReports.Report.defaults().withFailedOn("rate_limit").withTask(task);
    when(reports.turnFailed("ABC-1", report)).thenReturn(true);

    controller.turnFailure(
        "/wt/ABC-1-proj",
        new SessionHookReport(
            null,
            null,
            null,
            new SessionHookReport.Ending(null, "rate_limit", null),
            new SessionHookReport.Turn(null, null, java.util.List.of())));

    verify(wakes).arm("ABC-1");
  }

  @Test
  void armsNoWakeForTurnFailureThatIsNotUsageLimit() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));

    controller.turnFailure(
        "/wt/ABC-1-proj",
        new SessionHookReport(
            null,
            null,
            null,
            new SessionHookReport.Ending(null, "overloaded", null),
            new SessionHookReport.Turn(null, null, java.util.List.of())));

    verify(wakes, never()).arm("ABC-1");
  }

  @Test
  void cancelsTheWakeWhenTheSessionReportsWorking() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));

    controller.report("working", "/wt/ABC-1-proj", null);

    verify(wakes).cancel("ABC-1");
  }

  @Test
  void keepsTheWakeWhenTheSessionReportsIdle() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));

    controller.report("idle", "/wt/ABC-1-proj", null);

    verify(wakes, never()).cancel("ABC-1");
  }

  @Test
  void namesNoLogWhenThePayloadCarriedNone() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));

    controller.report("working", "/wt/ABC-1-proj", null);

    verify(reports)
        .record(
            "ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults().withTask(task));
  }

  @Test
  void dropsTranscriptPathItCannotBelieveAndSaysSoOnceInTheLog() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));

    try (FakeLogCapture log = new FakeLogCapture(AgentSessionController.class)) {
      controller.report(
          "working",
          "/wt/ABC-1-proj",
          new SessionHookReport("/logs/session.jsonl", "compact", null));
      final int warnedForAbsolute = log.events().size();
      controller.report(
          "working",
          "/wt/ABC-1-proj",
          new SessionHookReport("relative/session.jsonl", "compact", null));

      verify(reports)
          .record(
              "ABC-1",
              SessionProbe.State.WORKING,
              SessionReports.Report.defaults().withStartedBy("compact").withTask(task));
      assertThat(warnedForAbsolute).isZero();
      assertThat(log.events())
          .filteredOn(event -> "session hook field dropped".equals(event.getMessage()))
          .extracting(ILoggingEvent::getLevel)
          .containsExactly(Level.WARN);
    }
  }

  @Test
  void answersTheSessionWithWhateverTheReportProduced() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
    when(reports.record(
            "ABC-1",
            SessionProbe.State.WORKING,
            SessionReports.Report.defaults().withStartedBy("compact").withTask(task)))
        .thenReturn("re-read task_context.md");

    String answered =
        controller.report(
            "working", "/wt/ABC-1-proj", new SessionHookReport(null, "compact", null));

    assertThat(answered).isEqualTo("re-read task_context.md");
  }

  @Test
  void carriesWhatTheCliToldTheHumanSoOneEventCanMeanTwoDifferentWaits() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));

    controller.report(
        "idle",
        "/wt/ABC-1-proj",
        new SessionHookReport(null, null, "Claude needs your permission to use Bash"));

    verify(reports)
        .record(
            "ABC-1",
            SessionProbe.State.IDLE,
            SessionReports.Report.defaults()
                .withSaid("Claude needs your permission to use Bash")
                .withTask(task));
  }

  @Test
  void handsThePermissionPromptsToolToTheReportAndAnswersNothing() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));

    String answered =
        controller.permission(
            "/wt/ABC-1-proj",
            new SessionHookReport(
                null,
                null,
                null,
                new SessionHookReport.Ending(null, null, null),
                new SessionHookReport.Turn(null, "Bash", java.util.List.of())));

    verify(reports)
        .permissionAsked(
            "ABC-1", SessionReports.Report.defaults().withToolName("Bash").withTask(task));
    assertThat(answered).isEmpty();
  }

  @Test
  void tellsTheTurnEndWhetherRefusalAlreadySentItOn() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));

    controller.turnEnd(
        "/wt/ABC-1-proj",
        new SessionHookReport(
            null,
            null,
            null,
            new SessionHookReport.Ending(true, null, null),
            new SessionHookReport.Turn(null, null, java.util.List.of(Map.of("id", "b1")))));

    verify(reports).turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), true, true);
  }

  @Test
  void answersTheTurnEndWithTheVerdictOfTheReports() {
    when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
    when(reports.turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), false, false))
        .thenReturn("{\"decision\": \"block\"}");

    String answered = controller.turnEnd("/wt/ABC-1-proj", null);

    assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
  }

  @Test
  void refusesReportFromDirectoryNoTaskRunsIn() {
    StateService state = mock(StateService.class);
    when(state.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

    assertThatThrownBy(() ->
            new AgentSessionController(state, mock(SessionReports.class), wakes)
                .report("waiting", "/elsewhere", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("/elsewhere");
  }

  @Test
  void refusesStateThatIsNotOneOfJagtsOwn() {
    StateService state = mock(StateService.class);
    when(state.findByWorktree("/wt/ABC-1-proj"))
        .thenReturn(
            Optional.of(
                Map.entry(
                    "ABC-1",
                    TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

    assertThatThrownBy(() ->
            new AgentSessionController(state, mock(SessionReports.class), wakes)
                .report("busy", "/wt/ABC-1-proj", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("busy");
  }
}

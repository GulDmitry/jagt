package dev.jagt.orchestrator.service.report;

import static dev.jagt.orchestrator.service.TestProjectConfigBuilder.projectConfig;
import static dev.jagt.orchestrator.service.report.TestAgentStatusReportsBuilder.agentStatusReports;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TestStateServiceBuilder;
import dev.jagt.orchestrator.service.session.LinkRefusals;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.Untaken;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgentStatusReportsTest {

  private final LinkRefusals refusals = mock(LinkRefusals.class);
  private final WorktreeChanges worktreeChanges = mock(WorktreeChanges.class);
  private final ConfigService configService = mock(ConfigService.class);

  private final ConfigFile.ArbiterConfig actMaster =
      new ConfigFile.ArbiterConfig("act", null, null, null, null);

  private final ConfigFile verifying =
      ConfigFile.defaults()
          .withProjects(
              Map.of(
                  "proj", projectConfig().withVerifyCommand(List.of("./gradlew", "test")).build()));

  private final TaskState inProgress =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();

  @TempDir Path root;

  private StateService state;
  private AgentStatusReports reports;

  @BeforeEach
  void wireReports() {
    state = TestStateServiceBuilder.stateService().withRoot(root).build();
    reports = agentStatusReports().build(state, refusals, worktreeChanges, configService);
  }

  @Test
  void tellsTheSessionItsHandBackIsWaitingOnVerificationRatherThanOnTheHuman() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(verifying);

    String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

    assertThat(answer).contains("VERIFYING");
  }

  @Test
  void recordsOnTheSessionThatTheTurnReportedWhenReportLands() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.IN_PROGRESS, "going on", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().work().session().reportedAt()).isPositive();
  }

  @Test
  void handsGreenVerificationToReviewRatherThanVerifyingItAgain() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).alias("a1").build());
    when(configService.load()).thenReturn(verifying);

    reports.verified(true, "verified", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.REVIEW_PENDING);
  }

  @Test
  void reportsAnErrorWhenVerdictJagtMeasuredDoesNotLand() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVERTED).alias("a1").build());
    when(configService.load()).thenReturn(ConfigFile.defaults());
    try (FakeLogCapture log = new FakeLogCapture(AgentStatusReports.class)) {

      reports.verified(true, "verified", "ABC-1");

      assertThat(log.events())
          .filteredOn(event -> "verdict not landed".equals(event.getMessage()))
          .extracting(ILoggingEvent::getLevel)
          .containsExactly(Level.ERROR);
    }
  }

  @Test
  void answersReportOnVanishedTaskAsFactNotFieldToFix() {
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThatThrownBy(() -> reports.report(TaskStatus.IN_PROGRESS, "working", "ABC-9"))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.NO_SUCH_TASK));
  }

  @Test
  void tellsTheSessionWhereItsReportLandedInTheStateItWasWrittenTo() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVERTED).alias("a1").build());
    StateService staleView = spy(state);
    doReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()))
        .when(staleView)
        .task("ABC-1");
    when(configService.load()).thenReturn(ConfigFile.defaults());
    AgentStatusReports reports =
        agentStatusReports().build(staleView, refusals, worktreeChanges, configService);

    String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

    assertThat(answer)
        .startsWith("Task ABC-1 stays REVERTED")
        .endsWith("Your line was recorded (done)");
  }

  @Test
  void tellsTheSessionTheMasterReadsItsHandBackNext() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigFile.defaults().withArbiter(actMaster));

    String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

    assertThat(answer)
        .isEqualTo(
            "Task ABC-1 -> REVIEW_PENDING (done); the arbiter reads this round next;"
                + " end your turn");
  }

  @Test
  void readsTheOutcomeFromTheArgumentWhenTheMessageCarriesNoMarker() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "IN_PROGRESS", "which cache should this use", "question", null, Map.of()),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().standing().report())
        .isEqualTo(AgentReport.QUESTION);
  }

  @Test
  void recordsRoundWithDiffWhenTheWorktreeContradictsNoChangesClaim() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host/mr/1")
            .build());
    when(worktreeChanges.anyUncommitted(any())).thenReturn(true);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage("REVIEW_PENDING", "already handled", "no_changes", null, Map.of()),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().standing().report()).isEqualTo(AgentReport.PLAIN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"no_changes", "no-changes", "no changes"})
  void keepsNoChangesRoundThatTheWorktreeBearsOut(String outcome) {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host/mr/1")
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage("REVIEW_PENDING", "already handled", outcome, null, Map.of()),
        "ABC-1");

    TaskState after = state.task("ABC-1").orElseThrow();
    assertThat(after.standing().report()).isEqualTo(AgentReport.NO_CHANGES);
    assertThat(after.standing().message()).isEqualTo("already handled");
  }

  @Test
  void marksQuestionStoppedOnDeniedCallAsPermissionOnlyTheHumanGrants() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "IN_PROGRESS", "edits in the deploy worktree denied", "permission", null, Map.of()),
        "ABC-1");

    TaskState after = state.task("ABC-1").orElseThrow();
    assertThat(after.standing().report()).isEqualTo(AgentReport.PERMISSION);
    assertThat(after.standing().message()).isEqualTo("edits in the deploy worktree denied");
  }

  @Test
  void truncatesStatusMessageToOneDashboardLineWhenAgentSendsAnEssay() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.IN_PROGRESS, "root cause\nanalysis ".repeat(20), "ABC-1");

    String stored = state.task("ABC-1").orElseThrow().standing().message();
    assertThat(stored).hasSizeLessThanOrEqualTo(100).doesNotContain("\n").endsWith("…");
  }

  @Test
  void tellsTheSessionItsHandBackIsVerifiedBeforeTheMasterReadsIt() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(verifying.withArbiter(actMaster));

    String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

    assertThat(answer)
        .endsWith(
            "verification runs first, and the arbiter reads this round once it passes;"
                + " end your turn");
  }

  @Test
  void answersSessionsReportWithTheStatusItLandedOnAndItsOwnWords() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    String answer =
        reports.report(
            new AgentStatusMessage("IN_PROGRESS", "working", null, null, Map.of()), "ABC-1");

    assertThat(answer).isEqualTo("Task ABC-1 -> IN_PROGRESS (working)");
  }

  @Test
  void answersVerificationVerdictWithTheStatusItLanded() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).alias("a1").build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    String answer = reports.verified(true, "checks green", "ABC-1");

    assertThat(answer).startsWith("Task ABC-1 -> REVIEW_PENDING (checks green)");
  }

  @Test
  void answersTheUntakenRoundOnlyWhereTheSessionsReportNamesItsTag() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .untaken(new Untaken("a1b2c3d4e5f60718", List.of(), "ROUND"))
            .build();
    state.putTask("ABC-1", working);
    state.putTask("ABC-2", working);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "answered the previous round", null, null, Map.of()),
        "ABC-1");
    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "done, round a1b2c3d4", null, null, Map.of()),
        "ABC-2");

    assertThat(state.task("ABC-1").orElseThrow().round().untaken()).isNotNull();
    assertThat(state.task("ABC-2").orElseThrow().round().untaken()).isNull();
  }

  @Test
  void closesTheTurnSessionsReportAnswersAndLeavesOpenTheOneJagtsOwnReportDoesNot() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .turn(Turn.none().relayed("round", "d1", "", 100))
            .build();
    state.putTask("ABC-1", working);
    state.putTask("ABC-2", working);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(new AgentStatusMessage("IN_PROGRESS", "working", null, null, Map.of()), "ABC-1");
    reports.report(TaskStatus.IN_PROGRESS, "working", "ABC-2");

    assertThat(state.task("ABC-1").orElseThrow().work().turnOrNone().open()).isFalse();
    assertThat(state.task("ABC-2").orElseThrow().work().turnOrNone().open()).isTrue();
  }

  @Test
  void logsTheMoveReportMadeWhereTheStatusChanged() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
    when(configService.load()).thenReturn(ConfigFile.defaults());
    try (FakeLogCapture log = new FakeLogCapture(AgentStatusReports.class)) {

      reports.report(TaskStatus.IN_PROGRESS, "started", "ABC-1");

      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .containsExactly("agent status changed");
      assertThat(log.events().getFirst().getKeyValuePairs())
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains("status=IN_PROGRESS", "previous=NEW", "said=started");
    }
  }

  @Test
  void logsQuestionOnceWhenTheStatusStaysAndNothingWhenItIsRepeatedOrPlain() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(ConfigFile.defaults());
    try (FakeLogCapture log = new FakeLogCapture(AgentStatusReports.class)) {

      reports.report(
          new AgentStatusMessage("IN_PROGRESS", "working", null, null, Map.of()), "ABC-1");
      reports.report(
          new AgentStatusMessage("IN_PROGRESS", "which cache", "question", null, Map.of()),
          "ABC-1");
      reports.report(
          new AgentStatusMessage("IN_PROGRESS", "which cache", "question", null, Map.of()),
          "ABC-1");

      assertThat(log.events()).extracting(ILoggingEvent::getMessage).containsExactly("agent asked");
    }
  }

  @Test
  void logsLineHeldOffTheCardButNotQuestionAskedAgainWhileHeld() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVERTED).alias("a1").build());
    state.putTask(
        "ABC-2",
        TaskState.builder("proj", "/wt", TaskStatus.REVERTED)
            .alias("a2")
            .report(AgentReport.QUESTION)
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());
    try (FakeLogCapture log = new FakeLogCapture(AgentStatusReports.class)) {

      reports.report(
          new AgentStatusMessage("IN_PROGRESS", "back at it", null, null, Map.of()), "ABC-1");
      reports.report(
          new AgentStatusMessage("IN_PROGRESS", "which one", "question", null, Map.of()), "ABC-2");

      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .containsExactly("agent line held off the card");
      assertThat(log.events().getFirst().getKeyValuePairs())
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains("task=ABC-1");
    }
  }
}

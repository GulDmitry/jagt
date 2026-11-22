package dev.jagt.orchestrator.job.verdict;

import static dev.jagt.orchestrator.job.TestMasterAnswerTaskBuilder.askingTask;
import static dev.jagt.orchestrator.job.verdict.TestMasterAnswerJobBuilder.masterAnswerJob;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.turn.TaskMovedOn;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class MasterAnswerJobTest {

  private final ConfigService config = mock(ConfigService.class);
  private final MasterPanel panel = mock(MasterPanel.class);
  private final MasterVerdicts verdicts = mock(MasterVerdicts.class);

  @TempDir Path root;

  private StateService state;
  private MasterAnswerJob job;

  @BeforeEach
  void masterActingOverAnEmptyState() {
    state = stateService().withRoot(root).build();
    when(config.load()).thenReturn(masterAnswerJob().config());
    job = masterAnswerJob().build(state, config, panel, verdicts);
  }

  @Test
  void answersSessionThatStoppedToAskWhereTheMasterStandsInForTheHuman() {
    TaskState asking = askingTask().build();
    state.putTask("ABC-1", asking);
    when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean()))
        .thenReturn(new MasterPanel.Decision("keep v2 beside v3", ""));

    job.run();

    verify(verdicts)
        .answered("ABC-1", asking, "keep v2?", new MasterPanel.Decision("keep v2 beside v3", ""));
  }

  @Test
  void marksNothingUnansweredOnTaskWhoseTurnMovedOnWhileTheMasterRead() {
    state.putTask("ABC-1", askingTask().build());
    when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean()))
        .thenReturn(new MasterPanel.Decision("keep v2 beside v3", ""));
    when(verdicts.answered(anyString(), any(), anyString(), any()))
        .thenThrow(new TaskMovedOn("ABC-1"));

    job.run();

    assertThat(state.task("ABC-1").orElseThrow().work().arbiterOrNone().unanswered()).isEmpty();
  }

  @Test
  void leavesQuestionForPermissionToTheHumanWhereTheMasterActs() {
    state.putTask(
        "ABC-1",
        askingTask()
            .withStatus(TaskStatus.DEPLOY_CONFLICT)
            .withMessage("edits in the deploy worktree denied")
            .withReport(AgentReport.PERMISSION)
            .build());

    job.run();

    verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
  }

  @Test
  void leavesQuestionWithNoTextToTheHumanRatherThanRetryingIt() {
    state.putTask(
        "ABC-1", askingTask().withStatus(TaskStatus.REVIEW_PENDING).withMessage(null).build());

    job.run();

    TaskState left = state.task("ABC-1").orElseThrow();
    assertThat(left.work().arbiterOrNone().unanswered()).isEqualTo(left.standing().askedDigest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ship", "plan"})
  void leavesQuestionOnPlanToThePlanReviewWhateverTheMasterKeeps(String kept) {
    state.putTask(
        "ABC-1",
        askingTask()
            .withStatus(TaskStatus.PLAN_PENDING)
            .withMessage("plan revised, approve?")
            .build());
    when(config.load()).thenReturn(masterAnswerJob().withMine(List.of(kept)).config());

    job.run();

    verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
  }

  @Test
  void leavesTheQuestionToTheHumanWhoKeptAnswering() {
    TaskState asking = askingTask().build();
    state.putTask("ABC-1", asking);
    when(config.load()).thenReturn(masterAnswerJob().withMine(List.of("answer")).config());

    job.run();

    verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
  }

  @ParameterizedTest
  @EnumSource(TaskStop.Trigger.class)
  void leavesTheQuestionOfTaskJagtStoppedToTheHuman(TaskStop.Trigger trigger) {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .message("keep v2?")
            .report(AgentReport.QUESTION)
            .stop(new TaskStop(trigger, 1L, "the evidence"))
            .build());

    job.run();

    verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
  }

  @Test
  void changesTheApproachOnceThreeAnswersChangedNothingInTheTree() {
    TaskState asking =
        askingTask()
            .withStatus(TaskStatus.REVIEW_PENDING)
            .withMessage("override the gate?")
            .build();
    state.putTask("ABC-1", asking);
    when(verdicts.answersOverThisTree(asking)).thenReturn(3);
    when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean()))
        .thenReturn(new MasterPanel.Undecided("the read returned no findings"));

    job.run();

    verify(panel)
        .answer("ABC-1", asking, "override the gate?", masterAnswerJob().config().arbiter(), true);
  }

  @Test
  void stopsRunawayLoopAtTheNinthAnswerOverAnUnchangedTree() {
    TaskState asking =
        askingTask()
            .withStatus(TaskStatus.REVIEW_PENDING)
            .withMessage("override the gate?")
            .build();
    state.putTask("ABC-1", asking);
    when(verdicts.answersOverThisTree(asking)).thenReturn(9);

    job.run();

    verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
    assertThat(state.task("ABC-1").orElseThrow().work().arbiterOrNone().unanswered())
        .isEqualTo(TaskTurns.asked(asking));
  }

  @Test
  void answersNothingForSessionThatIsWorkingAndAsksNothing() {
    state.putTask(
        "ABC-1", askingTask().withMessage("working").withReport(AgentReport.PLAIN).build());

    job.run();

    verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
  }

  @Test
  void asksThePanelOnceAboutQuestionItsAnswerCouldNotBeActedOn() {
    TaskState asking = askingTask().build();
    state.putTask("ABC-1", asking);
    when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean()))
        .thenReturn(new MasterPanel.Undecided("the read returned no findings"));
    job.run();
    job.run();

    verify(panel, times(1)).answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean());
  }

  @Test
  void namesWhyTheMastersAnswerWasUnusable() {
    state.putTask("ABC-1", askingTask().build());
    when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean()))
        .thenReturn(new MasterPanel.Undecided("the worktree could not be listed"));
    try (FakeLogCapture log = new FakeLogCapture(MasterAnswerJob.class)) {
      job.run();

      ILoggingEvent unusable =
          assertThat(log.events())
              .filteredOn(ILoggingEvent::getMessage, "master answer unusable")
              .singleElement()
              .actual();
      assertThat(unusable.getKeyValuePairs())
          .anySatisfy(pair -> {
            assertThat(pair.key).isEqualTo("cause");
            assertThat(pair.value).isEqualTo("the worktree could not be listed");
          });
    }
  }

  @Test
  void asksThePanelOnceAboutQuestionWhoseAnswerTheSessionRefused() {
    TaskState asking = askingTask().build();
    state.putTask("ABC-1", asking);
    when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean()))
        .thenReturn(new MasterPanel.Decision("keep v2", ""));
    when(verdicts.answered(anyString(), any(), anyString(), any())).thenReturn(false);
    job.run();
    job.run();

    verify(panel, times(1)).answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean());
  }

  @Test
  void answersNoRepositoryRequestInProseWhereTheMasterHoldsTheRight() {
    state.putTask(
        "ABC-1",
        askingTask().withMessage("needs the registry").withRepoRequest("registry").build());

    job.run();

    verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
    assertThat(state.task("ABC-1").orElseThrow().work().arbiterOrNone().unanswered())
        .isEqualTo(TaskTurns.asked(state.task("ABC-1").orElseThrow()));
  }

  @Test
  void leavesRepositoryRequestToTheHumanWhoKeptTheRight() {
    state.putTask(
        "ABC-1",
        askingTask().withMessage("needs the registry").withRepoRequest("registry").build());
    when(config.load()).thenReturn(masterAnswerJob().withMine(List.of("repo")).config());

    job.run();

    verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
  }

  @Nested
  class WhereThePanelFindsNothingToDecide {

    @BeforeEach
    void panelUndecided() {
      when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean()))
          .thenReturn(new MasterPanel.Undecided("the read returned no findings"));
    }

    @Test
    void answersQuestionWhereTheMasterLeftAnEarlierStepToTheHuman() {
      TaskState asking = askingTask().build();
      state.putTask(
          "ABC-1", asking.withMaster(Master.defaults().withLeft("deploy src/Login.java")));

      job.run();

      verify(panel)
          .answer(eq("ABC-1"), any(), eq(asking.standing().message()), any(), anyBoolean());
    }

    @Test
    void answersQuestionOnTaskWhoseDeployItPressedAndWasRefused() {
      TaskState asking =
          askingTask().withStatus(TaskStatus.APPROVED).withLastActiveTimestamp(7).build();
      state.putTask("ABC-1", asking.withMaster(Master.defaults().withPressedDeploy("7")));

      job.run();

      verify(panel)
          .answer(eq("ABC-1"), any(), eq(asking.standing().message()), any(), anyBoolean());
    }

    @Test
    void answersQuestionThatCameAfterItSealedItsReadOfTheRound() {
      TaskState asking = askingTask().withStatus(TaskStatus.REVIEW_PENDING).build();
      state.putTask("ABC-1", asking.withMaster(Master.defaults().withSeal("fingerprint", 0)));

      job.run();

      verify(panel)
          .answer(eq("ABC-1"), any(), eq(asking.standing().message()), any(), anyBoolean());
    }
  }

  @Test
  void answersTheSameQuestionAgainWhereTheSessionWorkedSinceTheMasterLeftItUnanswered() {
    final FlowReports reports =
        new FlowReports(
            state,
            new FakeClock(),
            new Checkers(new TaskChart(Composition.builtIn())),
            new FlowRules(new TaskChart(Composition.builtIn())));
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean()))
        .thenReturn(new MasterPanel.Undecided("the read returned no findings"));
    reports.report("ABC-1", TaskStatus.IN_PROGRESS, AgentReport.QUESTION, "keep v2?");
    job.run();
    reports.report("ABC-1", TaskStatus.IN_PROGRESS, AgentReport.PLAIN, "worked on it");
    reports.report("ABC-1", TaskStatus.IN_PROGRESS, AgentReport.QUESTION, "keep v2?");

    job.run();

    verify(panel, times(2)).answer(anyString(), any(), anyString(), any(), anyBoolean());
  }
}

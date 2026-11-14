package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestConflictStallBuilder.conflictStall;
import static dev.jagt.orchestrator.job.TestConflictedTaskBuilder.conflictedTask;
import static dev.jagt.orchestrator.job.TestWaitingConflictBuilder.waitingConflict;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.DeployStep;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConflictStallTest {

  private final ConfigService config = mock(ConfigService.class);
  private final Clock clock = mock(Clock.class);

  @TempDir Path root;

  private StateService state;
  private ConflictStall stall;
  private final DeployConflicts.WaitingConflict conflict = waitingConflict().build();

  @BeforeEach
  void conflictHandedToTheWatch() {
    state = stateService().withRoot(root).build();
    state.putTask("ABC-42", conflictedTask().build());
    stall = conflictStall().build(state, config, clock);
    when(config.load()).thenReturn(conflictStall().config());
  }

  @Test
  void tellsTheHumanOnceWhenHandedConflictStopsMoving() {
    when(clock.millis()).thenReturn(1_000L, 1_201_000L, 2_401_000L);

    stall.watch("ABC-42", conflict);
    stall.watch("ABC-42", conflict);
    stall.watch("ABC-42", conflict);

    assertThat(state.task("ABC-42").orElseThrow().currentConflict())
        .hasValueSatisfying(watch -> assertThat(watch.stalled()).isTrue());
  }

  @Test
  void asksTheMasterInsteadOfTellingTheHumanOnTheFirstStallWhereItAnswers() {
    when(config.load()).thenReturn(conflictStall().withMasterMode("act").config());
    when(clock.millis()).thenReturn(1_000L, 1_201_000L);

    stall.watch("ABC-42", conflict);
    stall.watch("ABC-42", conflict);

    TaskState asked = state.task("ABC-42").orElseThrow();
    assertThat(asked.report()).isEqualTo(AgentReport.QUESTION);
    assertThat(asked.message()).contains("7 paths unmerged, 0 staged in /src/ABC-42-deploy");
    assertThat(asked.currentConflict().orElseThrow().askedTheMaster()).isTrue();
  }

  @Test
  void tellsTheHumanOnTheSecondStallOfConflictTheMasterWasAsked() {
    when(config.load()).thenReturn(conflictStall().withMasterMode("act").config());
    when(clock.millis()).thenReturn(1_000L, 1_201_000L, 2_401_000L);

    stall.watch("ABC-42", conflict);
    stall.watch("ABC-42", conflict);
    stall.watch("ABC-42", conflict);

    assertThat(state.task("ABC-42").orElseThrow().currentConflict().orElseThrow().told())
        .isNotNull();
  }

  @Test
  void tellsTheHumanOnTheFirstStallWhereQuestionOfTheSessionAlreadyStands() {
    state.putTask("ABC-42", conflictedTask().build().withReport(AgentReport.QUESTION));
    when(config.load()).thenReturn(conflictStall().withMasterMode("act").config());
    when(clock.millis()).thenReturn(1_000L, 1_201_000L);

    stall.watch("ABC-42", conflict);
    stall.watch("ABC-42", conflict);

    assertThat(state.task("ABC-42").orElseThrow().currentConflict().orElseThrow().askedTheMaster())
        .isFalse();
  }

  @Test
  void leavesConflictWhoseWorktreeChangedWithinTheWindowToTheSession() {
    when(clock.millis()).thenReturn(1_000L, 1_141_000L, 1_501_000L);
    DeployConflicts.WaitingConflict changed =
        waitingConflict().withProgress(new Conflict.Progress(7, 0, "b")).build();

    stall.watch("ABC-42", conflict);
    stall.watch("ABC-42", changed);
    stall.watch("ABC-42", changed);

    assertThat(state.task("ABC-42").orElseThrow().currentConflict())
        .hasValueSatisfying(watch -> assertThat(watch.stalled()).isFalse());
  }

  @Test
  void restartNeitherTellsTheHumanAgainNorHandsTheConflictAgain() {
    when(clock.millis()).thenReturn(1_000L, 1_201_000L, 3_601_000L, 4_801_000L);
    stall.watch("ABC-42", conflict);
    stall.watch("ABC-42", conflict);
    ConflictStall restarted =
        conflictStall().build(stateService().withRoot(root).build(), config, clock);

    final boolean handed = restarted.handed("ABC-42");
    restarted.watch("ABC-42", conflict);
    restarted.watch("ABC-42", conflict);

    assertThat(handed).isTrue();
    assertThat(state.task("ABC-42").orElseThrow().currentConflict().orElseThrow().told())
        .extracting(Conflict.Told::at)
        .isEqualTo(1_201_000L);
  }

  @Test
  void conflictFromNewDeployPressIsHandedAgain() {
    when(clock.millis()).thenReturn(1_000L);
    stall.watch("ABC-42", conflict);
    state.updateTask(
        "ABC-42",
        task ->
            new TaskReducer(new TaskChart(Composition.builtIn()))
                .deployStepped(
                    task, new DeployStep.Conflicted("web", Deployment.Cause.MERGE), 900_000));

    boolean handed = stall.handed("ABC-42");

    assertThat(handed).isFalse();
  }

  @Test
  void pointsStalledRevertConflictAtTheRevertWorktreeAndRevert() {
    state.putTask("ABC-42", conflictedTask().withCause(Deployment.Cause.REVERT).build());
    DeployConflicts.WaitingConflict revert =
        waitingConflict().withWorktree(Path.of("/src/ABC-42-revert")).build();
    when(clock.millis()).thenReturn(1_000L, 1_201_000L);
    stall.watch("ABC-42", revert);
    try (FakeLogCapture log = new FakeLogCapture(ConflictStall.class)) {
      stall.watch("ABC-42", revert);

      assertThat(log.events())
          .singleElement()
          .satisfies(event -> {
            assertThat(event.getMessage()).isEqualTo("revert conflict stalled");
            assertThat(event.getKeyValuePairs())
                .anySatisfy(pair -> {
                  assertThat(pair.key).isEqualTo("fix");
                  assertThat(String.valueOf(pair.value))
                      .isEqualTo("resolve it in the revert worktree, git add, then revert ABC-42");
                });
          });
    }
  }
}

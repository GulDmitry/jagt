package dev.jagt.orchestrator.job.conflict;

import static dev.jagt.orchestrator.job.TestWaitingConflictBuilder.waitingConflict;
import static dev.jagt.orchestrator.service.TestCaseDoorBuilder.caseDoor;
import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.session.Standing;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConflictHandOffTest {

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final ConflictStall stall = mock(ConflictStall.class);
  private final ConflictFinish finish = mock(ConflictFinish.class);

  @TempDir Path root;

  private StateService state;
  private ConflictHandOff handOff;

  @BeforeEach
  void setUp() {
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    state = stateService().withRoot(root).build();
    CaseDoor door =
        caseDoor()
            .withRoot(root)
            .build(
                state,
                taskStops().withRoot(root).build(state, Clock.systemUTC()),
                Clock.systemUTC());
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths),
            sessions,
            door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    handOff = new ConflictHandOff(relay, new ConflictHandover(sessions, relay), stall, finish);
  }

  @Test
  void handsConflictOnlyOnce() {
    when(stall.handed("ABC-42")).thenReturn(true);

    handOff.handle("ABC-42", waitingConflict().build());

    verify(sessions, never()).writeTaskContext(eq("ABC-42"), anyString());
  }

  @Test
  void watchesHandedConflictForProgress() {
    var conflict = waitingConflict().build();
    when(stall.handed("ABC-42")).thenReturn(true);

    handOff.handle("ABC-42", conflict);

    verify(stall).watch("ABC-42", conflict);
  }

  @Test
  void finishesNothingWhilePathsRemainUnstaged() {
    when(stall.handed("ABC-42")).thenReturn(true);

    handOff.handle("ABC-42", waitingConflict().build());

    verifyNoInteractions(finish);
  }

  @Test
  void finishesTheDeployOnceTheResolutionIsStagedInFull() {
    handOff.handle(
        "ABC-42",
        waitingConflict()
            .withStagedInFull(true)
            .withProgress(new Conflict.Progress(0, 7, "d"))
            .build());

    verify(finish).press("ABC-42", Path.of("/src/ABC-42-deploy"));
  }

  @Nested
  class TaskInDeployConflict {

    @BeforeEach
    void seed() throws Exception {
      Files.createDirectories(root.resolve("roles/session"));
      Files.copy(
          Path.of("../../roles/session/cases.md.dist"),
          root.resolve("roles/session/cases.md.dist"));
      state.putTask(
          "ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());
    }

    @Test
    void handsConflictToTheSessionAsWorktreeOfItsOwn() {
      handOff.handle(
          "ABC-42",
          waitingConflict()
              .withStanding(new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0))
              .build());

      verify(sessions).admit("ABC-42", Path.of("/src/ABC-42-deploy"));
      verify(sessions).writeTaskContext(eq("ABC-42"), contains("/src/ABC-42-deploy"));
    }

    @Test
    void watchesTheConflictItJustHandedToTheSessionForProgress() {
      var conflict =
          waitingConflict()
              .withStanding(new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0))
              .build();

      handOff.handle("ABC-42", conflict);

      verify(stall).watch("ABC-42", conflict);
    }

    @Test
    void handsDeployWhosePipelineWentRedAsFixOfTheMergeResultInItsWorktree() {
      handOff.handle(
          "ABC-42",
          waitingConflict()
              .withProgress(new Conflict.Progress(0, 0, "d"))
              .withFailure(
                  "red pipeline on the deploy commit: proj 01234567"
                      + " https://git.example.invalid/p/7")
              .withStanding(new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0))
              .build());

      verify(sessions).admit("ABC-42", Path.of("/src/ABC-42-deploy"));
      verify(sessions)
          .writeTaskContext(
              eq("ABC-42"),
              contains(
                  "ABC-42 deployed, but its red pipeline on the deploy commit: proj 01234567"));
      verify(sessions)
          .writeTaskContext(eq("ABC-42"), contains("Fix the merge result in /src/ABC-42-deploy"));
    }

    @Test
    void handsRevertConflictToTheSessionAsTheRevertWorktreeToResolve() {
      handOff.handle(
          "ABC-42",
          waitingConflict()
              .withWorktree(Path.of("/src/ABC-42-revert"))
              .withProgress(new Conflict.Progress(2, 0, "r"))
              .withRevert(true)
              .withStanding(new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0))
              .build());

      verify(sessions).admit("ABC-42", Path.of("/src/ABC-42-revert"));
      verify(sessions).writeTaskContext(eq("ABC-42"), contains("ABC-42 did not revert"));
    }

    @Test
    void handsNothingAndStopsNothingWhenTheConflictEndedSinceItWasRead() {
      state.putTask(
          "ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOYED).build());

      handOff.handle(
          "ABC-42",
          waitingConflict()
              .withProgress(new Conflict.Progress(0, 0, "d"))
              .withFailure(
                  "red pipeline on the deploy commit: proj 01234567"
                      + " https://git.example.invalid/p/7")
              .withStanding(new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0))
              .build());

      assertThat(state.task("ABC-42").orElseThrow().stop()).isNull();
      verify(sessions, never()).writeTaskContext(eq("ABC-42"), anyString());
    }
  }
}

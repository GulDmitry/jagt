package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConflictHandOffTest {

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final ConflictStall stall = mock(ConflictStall.class);
  private final ConflictFinish finish = mock(ConflictFinish.class);

  @Test
  void handsConflictToTheSessionAsWorktreeOfItsOwn(@TempDir Path root) throws Exception {
    ConflictHandOffs handOffs =
        new ConflictHandOffs(root, sessions, stall, finish).seeded(TaskStatus.DEPLOY_CONFLICT);

    handOffs.handOff.handle(
        "ABC-42",
        ConflictHandOffs.deployConflict(new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0)));

    verify(sessions).admit("ABC-42", Path.of("/src/ABC-42-deploy"));
    verify(sessions).writeTaskContext(eq("ABC-42"), contains("/src/ABC-42-deploy"));
  }

  @Test
  void watchesTheConflictItJustHandedToTheSessionForProgress(@TempDir Path root) throws Exception {
    ConflictHandOffs handOffs =
        new ConflictHandOffs(root, sessions, stall, finish).seeded(TaskStatus.DEPLOY_CONFLICT);
    var conflict =
        ConflictHandOffs.deployConflict(new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0));

    handOffs.handOff.handle("ABC-42", conflict);

    verify(stall).watch("ABC-42", conflict);
  }

  @Test
  void handsConflictOnlyOnce(@TempDir Path root) {
    ConflictHandOffs handOffs = new ConflictHandOffs(root, sessions, stall, finish);
    when(stall.handed("ABC-42")).thenReturn(true);

    handOffs.handOff.handle("ABC-42", ConflictHandOffs.deployConflict(Standing.ABSENT));

    verify(sessions, never()).writeTaskContext(eq("ABC-42"), anyString());
  }

  @Test
  void watchesHandedConflictForProgress(@TempDir Path root) {
    ConflictHandOffs handOffs = new ConflictHandOffs(root, sessions, stall, finish);
    var conflict = ConflictHandOffs.deployConflict(Standing.ABSENT);
    when(stall.handed("ABC-42")).thenReturn(true);

    handOffs.handOff.handle("ABC-42", conflict);

    verify(stall).watch("ABC-42", conflict);
  }

  @Test
  void finishesNothingWhilePathsRemainUnstaged(@TempDir Path root) {
    ConflictHandOffs handOffs = new ConflictHandOffs(root, sessions, stall, finish);
    when(stall.handed("ABC-42")).thenReturn(true);

    handOffs.handOff.handle("ABC-42", ConflictHandOffs.deployConflict(Standing.ABSENT));

    verifyNoInteractions(finish);
  }

  @Test
  void finishesTheDeployOnceTheResolutionIsStagedInFull(@TempDir Path root) {
    ConflictHandOffs handOffs = new ConflictHandOffs(root, sessions, stall, finish);

    handOffs.handOff.handle("ABC-42", ConflictHandOffs.stagedInFull());

    verify(finish).press("ABC-42", Path.of("/src/ABC-42-deploy"));
  }

  @Test
  void handsDeployWhosePipelineWentRedAsFixOfTheMergeResultInItsWorktree(@TempDir Path root)
      throws Exception {
    ConflictHandOffs handOffs =
        new ConflictHandOffs(root, sessions, stall, finish).seeded(TaskStatus.DEPLOY_CONFLICT);

    handOffs.handOff.handle("ABC-42", ConflictHandOffs.redPipeline());

    verify(sessions).admit("ABC-42", Path.of("/src/ABC-42-deploy"));
    verify(sessions)
        .writeTaskContext(
            eq("ABC-42"),
            contains("ABC-42 deployed, but its red pipeline on the deploy commit: proj 01234567"));
    verify(sessions)
        .writeTaskContext(eq("ABC-42"), contains("Fix the merge result in /src/ABC-42-deploy"));
  }

  @Test
  void handsRevertConflictToTheSessionAsTheRevertWorktreeToResolve(@TempDir Path root)
      throws Exception {
    ConflictHandOffs handOffs =
        new ConflictHandOffs(root, sessions, stall, finish).seeded(TaskStatus.DEPLOY_CONFLICT);

    handOffs.handOff.handle("ABC-42", ConflictHandOffs.revertConflict());

    verify(sessions).admit("ABC-42", Path.of("/src/ABC-42-revert"));
    verify(sessions).writeTaskContext(eq("ABC-42"), contains("ABC-42 did not revert"));
  }

  @Test
  void handsNothingAndStopsNothingWhenTheConflictEndedSinceItWasRead(@TempDir Path root)
      throws Exception {
    ConflictHandOffs handOffs =
        new ConflictHandOffs(root, sessions, stall, finish).seeded(TaskStatus.DEPLOYED);

    handOffs.handOff.handle("ABC-42", ConflictHandOffs.redPipeline());

    assertThat(handOffs.state.task("ABC-42").orElseThrow().stop()).isNull();
    verify(sessions, never()).writeTaskContext(eq("ABC-42"), anyString());
  }
}

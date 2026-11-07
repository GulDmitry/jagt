package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetiredWorktreesTest {

  private final GitWorktrees git = mock(GitWorktrees.class);
  private final AgentSessions sessions = mock(AgentSessions.class);
  private final OrchestratorPaths paths = mock(OrchestratorPaths.class);

  @Test
  void leavesTheWorktreeOnDiskWhenItsProjectIsGoneFromConfig() {
    boolean missing =
        new RetiredWorktrees(git, sessions, paths)
            .remove("ABC-1", List.of(TaskRepo.of("gone", "/wt")), Map.of());

    assertThat(missing).isTrue();
    verify(git, never()).removeWorktree(any(), any(), any());
  }

  @Test
  void hasTheAgentRuntimeForgetTheWorktreeEvenWhenItsProjectIsGoneFromConfig() {
    new RetiredWorktrees(git, sessions, paths)
        .remove("ABC-1", List.of(TaskRepo.of("gone", "/wt")), Map.of());

    verify(sessions).forgetWorktree(Path.of("/wt"));
  }

  @Test
  void discardsTheDiffCheckoutsEvenWhenTheProjectIsGoneFromConfig() {
    new RetiredWorktrees(git, sessions, paths)
        .remove("ABC-1", List.of(TaskRepo.of("gone", "/wt")), Map.of());

    verify(git).discardDiffWorktrees("ABC-1", "gone");
  }

  @Test
  void deletesTheWorktreeOfEveryRepositoryTheTaskWorkedIn() {
    new RetiredWorktrees(git, sessions, paths)
        .remove(
            "ABC-1",
            List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
            Map.of(
                "api", new ProjectConfig("/api-repo", "origin/main", "dev", List.of()),
                "web", new ProjectConfig("/web-repo", "origin/main", "dev", List.of())));

    verify(git).removeWorktree(Path.of("/api-repo"), Path.of("/api-wt"), null);
    verify(git).removeWorktree(Path.of("/web-repo"), Path.of("/web-wt"), null);
  }

  @Test
  void deletesTheThrowawayDiffCheckoutsOfTheRetiredTask() {
    new RetiredWorktrees(git, sessions, paths)
        .remove(
            "ABC-1",
            List.of(TaskRepo.of("demo", "/wt")),
            Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of())));

    verify(git).removeDiffWorktrees(Path.of("/repo"), "ABC-1", "demo");
  }

  @Test
  void hasTheAgentRuntimeUndoWhatItWroteOutsideTheRetiredWorktree() {
    new RetiredWorktrees(git, sessions, paths)
        .remove(
            "ABC-1",
            List.of(TaskRepo.of("demo", "/wt")),
            Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of())));

    verify(sessions).forgetWorktree(Path.of("/wt"));
  }

  @Test
  void removesTheDeployWorktreeAnAbandonedConflictLeftBehind() {
    new RetiredWorktrees(git, sessions, paths)
        .remove(
            "ABC-1",
            List.of(TaskRepo.of("demo", "/wt")),
            Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of())));

    verify(git).removeDeployWorktreeIfPresent(Path.of("/repo"), "ABC-1");
  }

  @Test
  void reportsNoMissingProjectWhereEveryRepositoryWasRemoved() {
    boolean missing =
        new RetiredWorktrees(git, sessions, paths)
            .remove(
                "ABC-1",
                List.of(TaskRepo.of("demo", "/wt")),
                Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of())));

    assertThat(missing).isFalse();
  }

  @Test
  void namesTheTrustedEntriesNoStayingRepositoryAlsoHolds() {
    TaskRepo web = TaskRepo.of("web", "/web-wt");
    TaskRepo api = TaskRepo.of("api", "/api-wt");
    TaskState task = TaskState.builder(List.of(api, web), TaskStatus.PLAN_PENDING).build();
    when(git.gitState(List.of(Path.of("/web-wt"))))
        .thenReturn(Map.of("core.bare in /web/.git/config", "a1", "/shared/hooks/pre-push", "b2"));
    when(git.gitState(List.of(Path.of("/api-wt"))))
        .thenReturn(Map.of("core.bare in /api/.git/config", "c3", "/shared/hooks/pre-push", "b2"));

    Set<String> own = new RetiredWorktrees(git, sessions, paths).trustedOnlyBy("ABC-42", task, web);

    assertThat(own).containsExactly("core.bare in /web/.git/config");
  }

  @Test
  void namesNoEntryWhereTheLeavingCheckoutCannotBeRead() {
    TaskRepo web = TaskRepo.of("web", "/web-wt");
    TaskState task =
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.PLAN_PENDING)
            .build();
    when(git.gitState(List.of(Path.of("/web-wt")))).thenThrow(new IllegalStateException("gone"));

    Set<String> own = new RetiredWorktrees(git, sessions, paths).trustedOnlyBy("ABC-42", task, web);

    assertThat(own).isEmpty();
  }

  @Test
  void ignoresAStayingRepositoryWithNoCheckoutWhenNamingTheLeavingOnesEntries() {
    TaskRepo web = TaskRepo.of("web", "/web-wt");
    TaskState task =
        TaskState.builder(List.of(TaskRepo.of("api", null), web), TaskStatus.PLAN_PENDING).build();
    when(git.gitState(List.of(Path.of("/web-wt"))))
        .thenReturn(Map.of("core.bare in /web/.git/config", "a1"));
    when(git.gitState(List.of())).thenReturn(Map.of());

    Set<String> own = new RetiredWorktrees(git, sessions, paths).trustedOnlyBy("ABC-42", task, web);

    assertThat(own).containsExactly("core.bare in /web/.git/config");
  }

  @Test
  void deletesTheScratchFolderOfATaskThatNeverLeftIt(@TempDir Path root) throws Exception {
    OrchestratorPaths real =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Path folder = Files.createDirectories(real.scratch().resolve("ABC-1"));
    Files.writeString(folder.resolve("plan.md"), "plan");

    new RetiredWorktrees(git, sessions, real)
        .remove("ABC-1", List.of(TaskRepo.of(null, folder.toString())), Map.of());

    assertThat(folder).doesNotExist();
    verify(sessions).forgetWorktree(folder);
    verify(git, never()).removeWorktree(any(), any(), any());
  }

  @Test
  void keepsAFolderOutsideTheScratchRootThatAStateFileNamedAsAScratchFolder(@TempDir Path root)
      throws Exception {
    OrchestratorPaths real =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Path elsewhere = Files.createDirectories(root.resolve("elsewhere"));

    new RetiredWorktrees(git, sessions, real)
        .remove("ABC-1", List.of(TaskRepo.of(null, elsewhere.toString())), Map.of());

    assertThat(elsewhere).exists();
  }
}

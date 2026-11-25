package dev.jagt.orchestrator.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.task.repo.Remote;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

class GitRevertUnitTest {

  @TempDir Path repo;
  @TempDir Path worktree;
  private final GitCommands git = mock(GitCommands.class);
  private final DeployWorktreePaths worktrees = mock(DeployWorktreePaths.class);
  private final WorktreeConflicts conflicts = mock(WorktreeConflicts.class);
  private final GitRevert revert = new GitRevert(git, worktrees, conflicts);

  @Test
  void listsTheUnmergedPathsOfTheTasksRevertWorktreeWhenItExists() {
    when(worktrees.revert(repo, "ABC-1")).thenReturn(worktree);
    when(git.worktreeOwner(worktree)).thenReturn(Optional.of(repo));
    when(conflicts.unmergedPaths(worktree)).thenReturn("a.txt\nb.txt");

    String unmerged = revert.revertUnmerged(repo, "ABC-1");

    assertThat(unmerged).isEqualTo("a.txt\nb.txt");
  }

  @Test
  void keepsTheRevertWorktreeAndSaysSoWhenTheResumedRevertIsRejectedOnPush() {
    when(worktrees.revert(repo, "ABC-1")).thenReturn(worktree);
    when(git.remote(repo)).thenReturn(Remote.ORIGIN);
    when(git.locked(eq(repo), ArgumentMatchers.<Supplier<String>>any()))
        .thenAnswer(call -> call.<Supplier<String>>getArgument(1).get());
    when(git.fetchPruning(Remote.ORIGIN, repo)).thenReturn(new Processes.Result(0, "", ""));
    when(git.worktreeOwner(worktree)).thenReturn(Optional.of(repo));
    when(git.run(worktree, List.of("git", "rev-parse", "-q", "--verify", "REVERT_HEAD")))
        .thenReturn(new Processes.Result(1, "", ""));
    when(git.run(worktree, List.of("git", "rev-list", "--count", "origin/dev..HEAD")))
        .thenReturn(new Processes.Result(0, "2\n", ""));
    when(conflicts.unmergedPaths(worktree)).thenReturn("");
    when(git.run(
            worktree,
            List.of(
                "git",
                "log",
                "--fixed-strings",
                "--grep=This reverts commit m1",
                "--format=%H",
                "-1")))
        .thenReturn(new Processes.Result(0, "abc\n", ""));
    when(git.publish(Remote.ORIGIN, worktree, "dev"))
        .thenReturn(new Processes.Result(1, "", "rejected"));

    assertThatThrownBy(() -> revert.revertMergeAndPush(repo, "ABC-1", "dev", "m1", List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is kept");
  }
}

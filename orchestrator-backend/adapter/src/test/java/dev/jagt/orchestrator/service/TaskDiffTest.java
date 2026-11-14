package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.GitRepos.commands;
import static dev.jagt.orchestrator.service.GitRepos.commit;
import static dev.jagt.orchestrator.service.GitRepos.freshWorktree;
import static dev.jagt.orchestrator.service.GitRepos.git;
import static dev.jagt.orchestrator.service.TestTaskDiffBuilder.taskDiff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskDiffTest {

  private final Processes runner = new ProcessRunner();
  private final StateService state = mock(StateService.class);
  private final ConfigService config = mock(ConfigService.class);

  @Test
  void showsWhatTheTaskChangedCommittedOrNotAndAddedFilesAgainstTheMergeBase(@TempDir Path dir)
      throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base\n");
    Path wt = dir.resolve("wt");
    freshWorktree(runner, repo, wt);
    Files.writeString(repo.resolve("other.txt"), "landed on the base meanwhile\n");
    commit(runner, repo, "base moves on");
    git(runner, repo, "push", "-q", "origin", "main");
    Files.writeString(wt.resolve("f.txt"), "task change\n");
    Files.writeString(wt.resolve("b.txt"), "added by the task\n");

    List<TaskDiff.RepoDiff> diffs =
        taskDiff().withRepo(repo).withWorktree(wt).build(state, config, runner).read("ABC-1");

    assertThat(diffs).hasSize(1);
    assertThat(diffs.getFirst().project()).isEqualTo("proj");
    assertThat(diffs.getFirst().base()).isEqualTo("origin/main");
    assertThat(diffs.getFirst().cut()).isFalse();
    assertThat(diffs.getFirst().diff())
        .contains("+task change", "-base", "+added by the task")
        .doesNotContain("other.txt");
  }

  @Test
  void cutsDiffPastTheCapAndSaysSo(@TempDir Path dir) throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base\n");
    Path wt = dir.resolve("wt");
    freshWorktree(runner, repo, wt);
    Files.writeString(wt.resolve("big.txt"), "line\n".repeat(30_000));

    TaskDiff.RepoDiff diff =
        taskDiff()
            .withRepo(repo)
            .withWorktree(wt)
            .build(state, config, runner)
            .read("ABC-1")
            .getFirst();

    assertThat(diff.cut()).isTrue();
    assertThat(diff.diff()).hasSize(TaskDiff.MAX_CHARS);
  }

  @Test
  void readsNothingForTaskWithoutRepositoryYet(@TempDir Path dir) {
    when(state.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(state.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(TaskRepo.of(null, "/scratch/ABC-1")), TaskStatus.PLAN_PENDING)
                    .build()));
    GitCommands commands = commands(runner);

    List<TaskDiff.RepoDiff> diffs =
        new TaskDiff(state, new DiffBases(config), new DiffCheckouts(commands), commands)
            .read("ABC-1");

    assertThat(diffs).isEmpty();
  }
}

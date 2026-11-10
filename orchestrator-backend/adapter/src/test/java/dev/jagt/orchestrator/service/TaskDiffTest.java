package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskDiffTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final Processes runner = new ProcessRunner();
  private final StateService state = mock(StateService.class);
  private final ConfigService config = mock(ConfigService.class);

  private Path repoWithOneCommit(Path dir) throws Exception {
    Path origin = dir.resolve("origin.git");
    Path repo = dir.resolve("repo");
    runner.run(
        dir, TIMEOUT, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
    runner.run(dir, TIMEOUT, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
    Files.writeString(repo.resolve("a.txt"), "base\n");
    commitAndPush(repo, "init");
    return repo;
  }

  private void commitAndPush(Path repo, String message) {
    runner.run(repo, TIMEOUT, List.of("git", "add", "."));
    runner.run(
        repo,
        TIMEOUT,
        List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", message));
    runner.run(repo, TIMEOUT, List.of("git", "push", "-q", "origin", "main"));
  }

  private TaskDiff diffOf(Path repo, Path worktree) {
    when(state.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(config.project("proj"))
        .thenReturn(new ProjectConfig(repo.toString(), "origin/main", "dev", null));
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("proj", worktree.toString())), TaskStatus.REVIEW_PENDING)
            .build();
    when(state.task("ABC-1")).thenReturn(Optional.of(task));
    GitCommands commands =
        new GitCommands(
            runner, new LsofWorktreeProcesses(runner), path -> Optional.empty(), stopped -> {});
    return new TaskDiff(state, new DiffBases(config), new DiffCheckouts(commands), commands);
  }

  @Test
  void showsWhatTheTaskChangedCommittedOrNotAndAddedFilesAgainstTheMergeBase(@TempDir Path dir)
      throws Exception {
    Path repo = repoWithOneCommit(dir);
    Path wt = dir.resolve("wt");
    GitCommands commands =
        new GitCommands(
            runner, new LsofWorktreeProcesses(runner), path -> Optional.empty(), stopped -> {});
    new GitWorktrees(commands)
        .createWorktree(repo, wt, "ABC-1", "origin/main", BranchStrategy.FRESH);
    Files.writeString(repo.resolve("other.txt"), "landed on the base meanwhile\n");
    commitAndPush(repo, "base moves on");
    Files.writeString(wt.resolve("a.txt"), "task change\n");
    Files.writeString(wt.resolve("b.txt"), "added by the task\n");

    List<TaskDiff.RepoDiff> diffs = diffOf(repo, wt).read("ABC-1");

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
    Path repo = repoWithOneCommit(dir);
    Path wt = dir.resolve("wt");
    GitCommands commands =
        new GitCommands(
            runner, new LsofWorktreeProcesses(runner), path -> Optional.empty(), stopped -> {});
    new GitWorktrees(commands)
        .createWorktree(repo, wt, "ABC-1", "origin/main", BranchStrategy.FRESH);
    Files.writeString(wt.resolve("big.txt"), "line\n".repeat(30_000));

    TaskDiff.RepoDiff diff = diffOf(repo, wt).read("ABC-1").getFirst();

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
    GitCommands commands =
        new GitCommands(
            runner, new LsofWorktreeProcesses(runner), path -> Optional.empty(), stopped -> {});

    List<TaskDiff.RepoDiff> diffs =
        new TaskDiff(state, new DiffBases(config), new DiffCheckouts(commands), commands)
            .read("ABC-1");

    assertThat(diffs).isEmpty();
  }
}

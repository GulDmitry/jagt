package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.GitRepos.commands;
import static dev.jagt.orchestrator.service.GitRepos.commit;
import static dev.jagt.orchestrator.service.GitRepos.freshWorktree;
import static dev.jagt.orchestrator.service.GitRepos.git;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskDiffTest {

  private final Processes runner = new ProcessRunner();
  private final StateService state = mock(StateService.class);
  private final ConfigService config = mock(ConfigService.class);
  private final GitCommands commands = commands(runner);
  private final TaskDiff taskDiff =
      new TaskDiff(state, new DiffBases(config), new DiffCheckouts(commands), commands);

  @TempDir Path dir;

  @BeforeEach
  void taskStandsInTheClonedWorktree() {
    when(state.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(config.project("proj"))
        .thenReturn(
            new ProjectConfig(dir.resolve("repo").toString(), "origin/main", "dev", List.of()));
    when(state.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(TaskRepo.of("proj", dir.resolve("wt").toString())),
                        TaskStatus.REVIEW_PENDING)
                    .build()));
  }

  @Test
  void showsWhatTheTaskChangedCommittedOrNotAndAddedFilesAgainstTheMergeBase() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base\n");
    Path wt = dir.resolve("wt");
    freshWorktree(runner, repo, wt);
    Files.writeString(repo.resolve("other.txt"), "landed on the base meanwhile\n");
    commit(runner, repo, "base moves on");
    git(runner, repo, "push", "-q", "origin", "main");
    Files.writeString(wt.resolve("f.txt"), "task change\n");
    Files.writeString(wt.resolve("b.txt"), "added by the task\n");

    List<TaskDiff.RepoDiff> diffs = taskDiff.read("ABC-1");

    assertThat(diffs).hasSize(1);
    assertThat(diffs.getFirst().project()).isEqualTo("proj");
    assertThat(diffs.getFirst().base()).isEqualTo("origin/main");
    assertThat(diffs.getFirst().cut()).isFalse();
    assertThat(diffs.getFirst().diff())
        .contains("+task change", "-base", "+added by the task")
        .doesNotContain("other.txt");
  }

  @Test
  void cutsDiffPastTheCapAndSaysSo() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base\n");
    Path wt = dir.resolve("wt");
    freshWorktree(runner, repo, wt);
    Files.writeString(wt.resolve("big.txt"), "line\n".repeat(30_000));

    TaskDiff.RepoDiff diff = taskDiff.read("ABC-1").getFirst();

    assertThat(diff.cut()).isTrue();
    assertThat(diff.diff()).hasSize(TaskDiff.MAX_CHARS);
  }

  @Test
  void readsNothingForTaskWithoutRepositoryYet() {
    when(state.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(state.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(TaskRepo.of(null, "/scratch/ABC-1")), TaskStatus.PLAN_PENDING)
                    .build()));

    List<TaskDiff.RepoDiff> diffs = taskDiff.read("ABC-1");

    assertThat(diffs).isEmpty();
  }
}

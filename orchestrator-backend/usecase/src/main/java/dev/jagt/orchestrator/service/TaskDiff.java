package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What a task changed, per repository, read from its worktree as it stands and kept nowhere. */
@Service
@RequiredArgsConstructor
public class TaskDiff {

  static final int MAX_CHARS = 100_000;

  /** {@code cut} says {@code diff} stops at {@link #MAX_CHARS} of a longer one. */
  public record RepoDiff(String project, String base, String diff, boolean cut) {}

  private final StateService stateService;
  private final DiffBases bases;
  private final DiffCheckouts checkouts;
  private final GitCommands git;

  public List<RepoDiff> read(String taskIdOrAlias) {
    String taskId = stateService.canonicalTaskId(taskIdOrAlias);
    TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
    if (task.unplaced()) {
      return List.of();
    }
    return task.repos().stream().map(repo -> of(taskId, task, repo)).toList();
  }

  private RepoDiff of(String taskId, TaskState task, TaskRepo repo) {
    DiffBases.Base base = bases.of(taskId, task, repo);
    Path worktree = Path.of(Objects.requireNonNull(repo.worktreePath()));
    String tree = checkouts.snapshotTree(worktree, taskId);
    String mergeBase =
        git.run(worktree, List.of("git", "merge-base", base.ref(), "HEAD"))
            .expectSuccess("git merge-base " + base.ref() + " in " + worktree)
            .stdout()
            .trim();
    String diff =
        git.run(worktree, List.of("git", "diff", "--no-color", "--no-ext-diff", mergeBase, tree))
            .expectSuccess("git diff of " + taskId)
            .stdout();
    boolean cut = diff.length() > MAX_CHARS;
    String shown = cut ? diff.substring(0, MAX_CHARS) : diff;
    return new RepoDiff(Objects.requireNonNull(repo.project()), base.ref(), shown, cut);
  }
}

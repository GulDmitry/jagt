package dev.jagt.orchestrator.capability.ide;

import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.service.DiffBases;
import dev.jagt.orchestrator.service.DiffCheckouts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A task opened as a static diff against what its request targets. */
@Component
@RequiredArgsConstructor
class IdeDiff {

  private final DiffBases bases;
  private final DiffCheckouts diffCheckouts;
  private final EditorDriver editorDriver;

  /**
   * One comparison per repository, each right side FROZEN at this call: the editor's own Refresh
   * does nothing.
   */
  String open(String taskId, TaskState task) {
    if (task.unplaced()) {
      return "Nothing to diff yet: " + taskId + " has no repository until its plan is approved";
    }
    List<String> against = new ArrayList<>();
    boolean many = task.repos().size() > 1;
    for (TaskRepo repo : task.repos()) {
      against.add((many ? repo.project() : "changes") + " vs " + openDiffOf(taskId, task, repo));
    }
    return "Opened STATIC diff of "
        + taskId
        + " ("
        + String.join(", ", against)
        + ") — snapshot, does not refresh; re-run for a fresh one";
  }

  /** The base it was read against. */
  private String openDiffOf(String taskId, TaskState task, TaskRepo repo) {
    DiffBases.Base base = bases.of(taskId, task, repo);
    String project = Objects.requireNonNull(repo.project());
    Path baseCheckout =
        diffCheckouts.checkoutBaseForDiff(base.projectPath(), base.ref(), taskId, project);
    Path clean =
        diffCheckouts.checkoutWorktreeCleanForDiff(
            Path.of(repo.worktreePath()), base.projectPath(), base.ref(), taskId, project);
    editorDriver.openDiff(baseCheckout, clean);
    return base.ref();
  }
}

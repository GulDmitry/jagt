package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.BranchStrategy;
import java.nio.file.Path;
import java.util.Optional;

final class TaskDiffFixtures {

  private TaskDiffFixtures() {}

  static GitCommands commandsOver(Processes runner) {
    return new GitCommands(
        runner, new LsofWorktreeProcesses(runner), path -> Optional.empty(), stopped -> {});
  }

  static void freshWorktree(Processes runner, Path repo, Path worktree) {
    new GitWorktrees(commandsOver(runner))
        .createWorktree(repo, worktree, "ABC-1", "origin/main", BranchStrategy.FRESH);
  }
}

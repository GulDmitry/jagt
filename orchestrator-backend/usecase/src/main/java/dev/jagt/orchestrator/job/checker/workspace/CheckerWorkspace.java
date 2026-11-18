package dev.jagt.orchestrator.job.checker.workspace;

import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.config.role.RoleOptions;
import dev.jagt.orchestrator.service.role.RoleSettings;
import lombok.RequiredArgsConstructor;

/**
 * Offers a finished check's workspace to its request where {@code workspace.mergeRequest} is on.
 */
@RequiredArgsConstructor
public class CheckerWorkspace {

  private final RoleDefinition role;
  private final RoleSettings settings;
  private final WorkspaceRequests requests;

  public void offer(String taskId) {
    RoleOptions options = settings.load();
    if (options.workspaceMergeRequest()) {
      options.workspacePath().ifPresent(folder -> requests.propose(role.id(), folder, taskId));
    }
  }
}

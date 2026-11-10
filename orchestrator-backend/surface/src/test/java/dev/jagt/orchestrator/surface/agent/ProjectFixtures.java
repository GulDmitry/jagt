package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.task.ProjectConfig;
import java.util.List;

final class ProjectFixtures {

  private ProjectFixtures() {}

  static ProjectConfig sharingBranchesThrough(String base, String deploy, String remote) {
    return new ProjectConfig(
        new ProjectConfig.Repository("/repo", List.of(), null, List.of()),
        new ProjectConfig.Branches(base, deploy, remote),
        new ProjectConfig.CodeHost(
            null, null, List.of(), List.of(), List.of(), List.of(), List.of()),
        new ProjectConfig.Verify(List.of(), null));
  }
}

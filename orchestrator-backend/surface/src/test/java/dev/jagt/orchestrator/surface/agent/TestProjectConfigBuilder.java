package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.util.List;
import lombok.With;

@With
public record TestProjectConfigBuilder(String base, String deploy, String remote) {

  public static TestProjectConfigBuilder projectConfig() {
    return new TestProjectConfigBuilder("main", "dev", "origin");
  }

  public ProjectConfig build() {
    return new ProjectConfig(
        new ProjectConfig.Repository("/repo", List.of(), null, List.of(), null, null),
        new ProjectConfig.Branches(base, deploy, remote),
        ProjectConfig.CodeHost.defaults(),
        new ProjectConfig.Verify(List.of(), null),
        new ProjectConfig.Setup(List.of(), null));
  }
}

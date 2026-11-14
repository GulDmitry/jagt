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
        new ProjectConfig.Repository("/repo", List.of(), null, List.of()),
        new ProjectConfig.Branches(base, deploy, remote),
        new ProjectConfig.CodeHost(
            null, null, List.of(), List.of(), List.of(), List.of(), List.of()),
        new ProjectConfig.Verify(List.of(), null));
  }
}

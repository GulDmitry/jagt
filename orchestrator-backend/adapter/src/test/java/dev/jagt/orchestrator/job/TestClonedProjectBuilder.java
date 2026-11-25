package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.util.List;
import lombok.With;

@With
public record TestClonedProjectBuilder(String url, String path) {

  public static TestClonedProjectBuilder clonedProject() {
    return new TestClonedProjectBuilder("https://example.invalid/api.git", "/repos/api");
  }

  public ProjectConfig build() {
    return new ProjectConfig(
        new ProjectConfig.Repository(path, List.of(), null, List.of(), url, null),
        new ProjectConfig.Branches("origin/main", null, null),
        ProjectConfig.CodeHost.defaults(),
        new ProjectConfig.Verify(List.of(), null),
        new ProjectConfig.Setup(List.of(), null, List.of()));
  }
}

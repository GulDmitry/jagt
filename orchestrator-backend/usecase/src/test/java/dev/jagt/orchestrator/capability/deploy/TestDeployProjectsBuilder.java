package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.util.List;
import java.util.Map;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestDeployProjectsBuilder(
    @Nullable String projDeployBranch,
    @Nullable String apiDeployBranch,
    @Nullable String webDeployBranch) {

  public static TestDeployProjectsBuilder deployProjects() {
    return new TestDeployProjectsBuilder("dev", "dev", "dev");
  }

  public ConfigFile build() {
    return ConfigFile.defaults()
        .withProjects(
            Map.of(
                "proj",
                new ProjectConfig("/repo", "origin/main", projDeployBranch, List.of()),
                "api",
                new ProjectConfig("/repo/api", "origin/main", apiDeployBranch, List.of()),
                "web",
                new ProjectConfig("/repo/web", "origin/main", webDeployBranch, List.of())));
  }
}

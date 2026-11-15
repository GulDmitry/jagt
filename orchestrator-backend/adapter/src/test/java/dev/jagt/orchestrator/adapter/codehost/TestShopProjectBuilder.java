package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestShopProjectBuilder(@Nullable String kind, List<String> pipelineCommand) {

  public static TestShopProjectBuilder shopProject() {
    return new TestShopProjectBuilder(null, List.of());
  }

  public ProjectConfig build() {
    return new ProjectConfig(
        new ProjectConfig.Repository("/repo/shop", List.of(), null, List.of(), null, null),
        new ProjectConfig.Branches("origin/main", "dev", null),
        new ProjectConfig.CodeHost(
            kind, null, pipelineCommand, List.of(), List.of(), List.of(), List.of()),
        new ProjectConfig.Verify(List.of(), null),
        new ProjectConfig.Setup(List.of(), null));
  }
}

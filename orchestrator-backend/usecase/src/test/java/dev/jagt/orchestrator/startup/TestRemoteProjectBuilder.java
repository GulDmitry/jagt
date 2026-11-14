package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.task.ProjectConfig;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestRemoteProjectBuilder(@Nullable String codeHost, @Nullable String remote) {

  public static TestRemoteProjectBuilder remoteProject() {
    return new TestRemoteProjectBuilder(null, null);
  }

  public ProjectConfig build() {
    return new ProjectConfig(
        new ProjectConfig.Repository("/repo/shop", List.of(), null, List.of()),
        new ProjectConfig.Branches("main", "dev", remote),
        new ProjectConfig.CodeHost(
            codeHost, null, List.of(), List.of(), List.of(), List.of(), List.of()),
        new ProjectConfig.Verify(List.of(), null));
  }
}

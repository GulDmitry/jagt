package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestProjectConfigBuilder(
    String path,
    String baseBranch,
    @Nullable String deployBranch,
    @Nullable String remote,
    @Nullable String codeHost,
    List<String> verifyCommand,
    @Nullable Integer verifyMinutes) {

  public static TestProjectConfigBuilder projectConfig() {
    return new TestProjectConfigBuilder("/repo", "origin/main", "dev", null, null, List.of(), null);
  }

  public ProjectConfig build() {
    return new ProjectConfig(
        new ProjectConfig.Repository(path, List.of(), null, List.of()),
        new ProjectConfig.Branches(baseBranch, deployBranch, remote),
        new ProjectConfig.CodeHost(
            codeHost, null, List.of(), List.of(), List.of(), List.of(), List.of()),
        new ProjectConfig.Verify(verifyCommand, verifyMinutes));
  }
}

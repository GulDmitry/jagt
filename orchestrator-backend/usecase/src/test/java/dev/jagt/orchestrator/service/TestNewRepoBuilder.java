package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import lombok.With;

@With
public record TestNewRepoBuilder(
    String project, Path projectPath, Path worktree, Path gitDir, String baseBranch,
    boolean primary) {

  public static TestNewRepoBuilder api(Path root) {
    return new TestNewRepoBuilder(
        "api", Path.of("/api"), root.resolve("ABC-9-api"), root.resolve(".git"), "origin/main",
        true);
  }

  public static TestNewRepoBuilder web(Path root) {
    return new TestNewRepoBuilder(
        "web", Path.of("/web"), root.resolve("ABC-9-web"), root.resolve(".git"), "origin/main",
        false);
  }

  public NewRepo build() {
    return new NewRepo(
        project,
        new ProjectConfig(projectPath.toString(), "origin/main", "dev", List.of()),
        projectPath,
        worktree,
        gitDir,
        baseBranch,
        "",
        primary);
  }
}

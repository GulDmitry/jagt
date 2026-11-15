package dev.jagt.orchestrator.service.git;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.service.ConfigService;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Where the deploy and revert worktrees of a repository stand, found from its configured path. */
@Component
@RequiredArgsConstructor
public class DeployWorktreePaths {

  private final ConfigService configService;
  private final OrchestratorPaths paths;

  public Path deploy(Path projectPath, String taskId) {
    return paths.deployWorktree(keyOf(projectPath), taskId);
  }

  public Path revert(Path projectPath, String taskId) {
    return paths.revertWorktree(keyOf(projectPath), taskId);
  }

  private String keyOf(Path projectPath) {
    return configService.load().projects().entrySet().stream()
        .filter(
            project -> GitCommands.sameDirectory(Path.of(project.getValue().path()), projectPath))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(() ->
            new IllegalStateException("No project in jagt.yml has the path " + projectPath + "."));
  }
}

package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.git.GitCommands;
import dev.jagt.orchestrator.service.git.WorktreeOrphans;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.With;

@With
public record TestWorktreeOrphanScannerBuilder(Path repo, List<String> copyGlobs) {

  public static TestWorktreeOrphanScannerBuilder worktreeOrphanScanner() {
    return new TestWorktreeOrphanScannerBuilder(Path.of("/demo-repo"), List.of("**/.env"));
  }

  public ConfigService.ConfigFile config() {
    return ConfigService.ConfigFile.defaults()
        .withProjects(
            Map.of("demo", new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
        .withWorktree(ConfigService.ConfigFile.WorktreeConfig.defaults().withCopyGlobs(copyGlobs));
  }

  public WorktreeOrphanScanner build(
      ConfigService config,
      StateService state,
      GitCommands git,
      OrchestratorPaths paths,
      Notifications notifications) {
    return new WorktreeOrphanScanner(new WorktreeOrphans(config, state, git, paths), notifications);
  }
}

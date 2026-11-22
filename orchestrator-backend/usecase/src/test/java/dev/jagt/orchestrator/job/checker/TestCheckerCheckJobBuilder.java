package dev.jagt.orchestrator.job.checker;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.RoleSettings;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import lombok.With;

@With
public record TestCheckerCheckJobBuilder(Path roleFile) {

  public static TestCheckerCheckJobBuilder checkerCheckJob() {
    return new TestCheckerCheckJobBuilder(Path.of("../../roles/acceptance/role.yml.dist"));
  }

  public ConfigFile config() {
    return ConfigFile.defaults()
        .withRoles(Map.of("acceptance", new ConfigFile.RoleConfig(true, null)));
  }

  public CheckerCheckJob build(
      ConfigService config,
      OrchestratorPaths paths,
      CheckerReads reader,
      CheckerOutcomes outcomes,
      Clock clock) {
    RoleDefinition role = RoleDefinitions.fromFile(roleFile);
    return new CheckerCheckJob(
        new RoleSettings(role, config, paths),
        new CheckerPrompts(role, new RoleFiles(paths, "acceptance")),
        reader,
        new CheckerRecords(paths, clock, "acceptance"),
        outcomes);
  }
}

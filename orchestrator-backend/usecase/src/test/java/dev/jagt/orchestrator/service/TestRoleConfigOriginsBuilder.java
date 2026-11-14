package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.With;

@With
public record TestRoleConfigOriginsBuilder(Path root, String humanCopy) {

  public static TestRoleConfigOriginsBuilder roleConfigOrigins() {
    return new TestRoleConfigOriginsBuilder(Path.of("/state"), "");
  }

  public RoleConfigOrigins build(ConfigService config) throws IOException {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.writeString(root.resolve("jagt.yml"), "");
    Files.writeString(root.resolve("roles/acceptance/role.yml"), humanCopy);
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    return new RoleConfigOrigins(new InstalledRoles(paths), config, paths);
  }
}

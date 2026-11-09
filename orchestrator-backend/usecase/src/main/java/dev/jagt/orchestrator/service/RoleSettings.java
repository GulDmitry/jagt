package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.config.RoleOptions;

/** A role not installed is always off. */
public record RoleSettings(RoleDefinition role, ConfigService config, OrchestratorPaths paths) {

  public RoleOptions load() {
    if (!role.installed()) {
      return RoleOptions.defaults();
    }
    RoleOptions folder = RoleOptions.read(paths.root().resolve("roles").resolve(role.id()));
    return folder
        .withEnabled(config.load().role(role.id()).enabledOrDefault())
        .withModel(folder.model().isEmpty() ? role.model() : folder.model());
  }

  public String file() {
    return "roles/" + role.id() + "/role.yml";
  }
}

package dev.jagt.orchestrator.service.role;

import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.config.role.RoleFiles;

public record RoleKit(RoleSettings settings, RoleFiles files) {

  public RoleDefinition definition() {
    return settings.role();
  }
}

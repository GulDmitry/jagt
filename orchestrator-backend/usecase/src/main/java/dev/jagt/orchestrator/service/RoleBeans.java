package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.flow.Checkers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RoleBeans {

  @Bean
  RoleDefinition acceptanceDefinition(InstalledRoles roles) {
    return roles
        .find(Checkers.ACCEPTANCE)
        .orElseGet(() -> RoleDefinition.absent(Checkers.ACCEPTANCE));
  }

  @Bean
  RoleFiles acceptanceFiles(RoleDefinition acceptanceDefinition, OrchestratorPaths paths) {
    return new RoleFiles(paths, acceptanceDefinition.id());
  }

  @Bean
  RoleSettings acceptanceSettings(
      RoleDefinition acceptanceDefinition, ConfigService config, OrchestratorPaths paths) {
    return new RoleSettings(acceptanceDefinition, config, paths);
  }
}

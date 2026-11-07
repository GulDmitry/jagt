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
  RoleDefinition qaDefinition(InstalledRoles roles) {
    return roles.find(Checkers.QA).orElseGet(() -> RoleDefinition.absent(Checkers.QA));
  }

  @Bean
  RoleFiles qaFiles(RoleDefinition qaDefinition, OrchestratorPaths paths) {
    return new RoleFiles(paths, qaDefinition.id());
  }

  @Bean
  RoleSettings qaSettings(RoleDefinition qaDefinition, ConfigService config) {
    return new RoleSettings(qaDefinition, config);
  }
}

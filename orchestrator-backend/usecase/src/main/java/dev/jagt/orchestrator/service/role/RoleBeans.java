package dev.jagt.orchestrator.service.role;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.service.ConfigService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RoleBeans {

  @Bean
  RoleKits roleKits(InstalledRoles roles, ConfigService config, OrchestratorPaths paths) {
    List<RoleDefinition> definitions = new ArrayList<>(roles.definitions());
    if (roles.find(Checkers.ACCEPTANCE).isEmpty()) {
      definitions.add(RoleDefinition.absent(Checkers.ACCEPTANCE));
    }
    return new RoleKits(
        definitions.stream()
            .map(role ->
                new RoleKit(new RoleSettings(role, config, paths), new RoleFiles(paths, role.id())))
            .toList());
  }

  @Bean
  RoleFiles acceptanceFiles(RoleKits kits) {
    return kits.find(Checkers.ACCEPTANCE).orElseThrow().files();
  }

  @Bean
  RoleSettings acceptanceSettings(RoleKits kits) {
    return kits.find(Checkers.ACCEPTANCE).orElseThrow().settings();
  }
}

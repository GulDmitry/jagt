package dev.jagt.orchestrator.job.checker.wiring;

import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.service.role.RoleKits;
import dev.jagt.orchestrator.service.role.RoleSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AcceptanceBeans {

  @Bean
  RoleKits.Expected acceptanceExpected() {
    return new RoleKits.Expected(Checkers.ACCEPTANCE);
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

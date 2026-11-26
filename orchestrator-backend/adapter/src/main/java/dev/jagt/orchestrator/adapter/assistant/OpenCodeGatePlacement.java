package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.host.Layout.OlderPlacement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenCodeGatePlacement {

  static final String FOLDER = "opencode-gate";

  @Bean
  OlderPlacement openCodeGateFolder(OrchestratorPaths paths) {
    return new OlderPlacement(FOLDER, paths.layout().beside(FOLDER));
  }
}

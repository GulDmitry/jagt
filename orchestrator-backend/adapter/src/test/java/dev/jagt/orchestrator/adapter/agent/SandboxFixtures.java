package dev.jagt.orchestrator.adapter.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AgentConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.SandboxConfig;

final class SandboxFixtures {

  private SandboxFixtures() {}

  static ConfigService sandboxEnabled() {
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(
                    AgentConfig.defaults()
                        .withSandbox(SandboxConfig.defaults().withEnabled(true))));
    return config;
  }

  static Host hostWith(String... binaries) {
    Host host = mock(Host.class);
    for (String binary : binaries) {
      when(host.onPath(binary)).thenReturn(true);
    }
    return host;
  }
}

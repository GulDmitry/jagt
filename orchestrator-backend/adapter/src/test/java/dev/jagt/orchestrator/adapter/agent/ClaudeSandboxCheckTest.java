package dev.jagt.orchestrator.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AgentConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.SandboxConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaudeSandboxCheckTest {

  @Test
  void refusesToStartOnLinuxWhenTheSandboxLacksItsRelay() {
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(
                    AgentConfig.defaults()
                        .withSandbox(SandboxConfig.defaults().withEnabled(true))));
    Host host = mock(Host.class);
    when(host.onPath("bwrap")).thenReturn(true);

    List<Problem> problems = new ClaudeSandboxCheck(config, host).problems();

    assertThat(problems)
        .extracting(Problem::key)
        .containsExactly("orchestrator.agent.sandbox.enabled");
  }

  @Test
  void reportsNothingOnLinuxOnceBothRelaysAreInstalled() {
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(
                    AgentConfig.defaults()
                        .withSandbox(SandboxConfig.defaults().withEnabled(true))));
    Host host = mock(Host.class);
    when(host.onPath("bwrap")).thenReturn(true);
    when(host.onPath("socat")).thenReturn(true);

    List<Problem> problems = new ClaudeSandboxCheck(config, host).problems();

    assertThat(problems).isEmpty();
  }
}

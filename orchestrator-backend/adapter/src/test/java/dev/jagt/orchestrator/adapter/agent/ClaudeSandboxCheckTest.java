package dev.jagt.orchestrator.adapter.agent;

import static dev.jagt.orchestrator.adapter.agent.SandboxFixtures.hostWith;
import static dev.jagt.orchestrator.adapter.agent.SandboxFixtures.sandboxEnabled;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.service.ConfigService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaudeSandboxCheckTest {

  @Test
  void refusesToStartOnLinuxWhenTheSandboxLacksItsRelay() {
    ConfigService config = sandboxEnabled();
    Host host = hostWith("bwrap");

    List<Problem> problems = new ClaudeSandboxCheck(config, host).problems();

    assertThat(problems)
        .extracting(Problem::key)
        .containsExactly("orchestrator.agent.sandbox.enabled");
  }

  @Test
  void reportsNothingOnLinuxOnceBothRelaysAreInstalled() {
    ConfigService config = sandboxEnabled();
    Host host = hostWith("bwrap", "socat");

    List<Problem> problems = new ClaudeSandboxCheck(config, host).problems();

    assertThat(problems).isEmpty();
  }
}

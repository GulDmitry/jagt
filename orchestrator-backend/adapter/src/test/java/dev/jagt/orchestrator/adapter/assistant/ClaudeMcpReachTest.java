package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ClaudeMcpReachTest {

  @Test
  void theStandingMarkClearsOnTheNextRereadOnceTheServersAreBack() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                "acme tracker: https://mcp.example.com/mcp (HTTP) - ! Needs authentication\n",
                ""),
            new Processes.Result(
                0, "acme tracker: https://mcp.example.com/mcp (HTTP) - ✔ Connected\n", ""));
    ReachLedger reach = new ReachLedger();
    ClaudeMcpHealthProbe probe =
        new ClaudeMcpHealthProbe(
            runner, ClaudeProperties.defaults(), AssistantProperties.empty(), reach);
    probe.brokenServers();

    new ClaudeMcpReach(reach, probe).run();

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void noServerIsAskedAboutWhileNoMarkStands() {
    ProcessRunner runner = mock(ProcessRunner.class);
    ReachLedger reach = new ReachLedger();

    new ClaudeMcpReach(
            reach,
            new ClaudeMcpHealthProbe(
                runner, ClaudeProperties.defaults(), AssistantProperties.empty(), reach))
        .run();

    verifyNoInteractions(runner);
  }
}

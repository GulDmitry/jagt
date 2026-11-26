package dev.jagt.orchestrator.adapter.assistant.reach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClaudeMcpReachTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);

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
            runner,
            ClaudeProperties.defaults(),
            AssistantProperties.empty(),
            reach,
            FakeHost.machine());
    probe.brokenServers();

    new ClaudeMcpReach(reach, probe, clock).run();

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void theBoardNamesServerNeedingLoginBeforeAnyReadFailed() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                "acme tracker: https://mcp.example.com/mcp (HTTP) - ! Needs authentication\n",
                ""));
    ReachLedger reach = new ReachLedger();

    new ClaudeMcpReach(
            reach,
            new ClaudeMcpHealthProbe(
                runner,
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                reach,
                FakeHost.machine()),
            clock)
        .run();

    assertThat(reach.standing())
        .extracting(ReachLost::said)
        .containsExactly("MCP server down: acme tracker (Needs authentication)");
  }

  @Test
  void noServerIsStartedAgainWithinTenMinutesWhileAllAreConnected() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(new Processes.Result(0, "context7: npx - ✔ Connected\n", ""));
    ReachLedger reach = new ReachLedger();
    ClaudeMcpReach job =
        new ClaudeMcpReach(
            reach,
            new ClaudeMcpHealthProbe(
                runner,
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                reach,
                FakeHost.machine()),
            clock);

    job.run();
    job.run();

    verify(runner, times(1)).run(any(Path.class), any(Duration.class), any());
  }
}

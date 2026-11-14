package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaudeMcpHealthProbeTest {

  @Test
  void namesEveryServerReadCannotUseAndNoneItCan() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                Checking MCP server health...
                context7: npx -y @upstash/context7-mcp - ✔ Connected
                plugin:acme:gitlab: sh -c exec docker run -i --rm acme/gitlab-mcp - ✘ Failed to connect \
                — -32000: Connection closed
                acme tracker: https://mcp.example.com/mcp (HTTP) - ! Needs authentication
                """,
                ""));

    var broken =
        new ClaudeMcpHealthProbe(
                runner, ClaudeProperties.defaults(), AssistantProperties.empty(), mock(Reach.class))
            .brokenServers();

    assertThat(broken)
        .contains(
            List.of(
                "plugin:acme:gitlab (Failed to connect)", "acme tracker (Needs authentication)"));
  }

  @Test
  void marksReachLostWithItsLoginWhenOneServerIsDown() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                "acme tracker: https://mcp.example.com/mcp (HTTP) - ! Needs authentication\n",
                ""));
    Reach reach = mock(Reach.class);

    new ClaudeMcpHealthProbe(
            runner, ClaudeProperties.defaults(), AssistantProperties.empty(), reach)
        .brokenServers();

    verify(reach)
        .lost(
            new ReachLost(
                "claude-mcp",
                "mcp",
                List.of("claude"),
                "MCP server down: acme tracker (Needs authentication)"));
  }

  @Test
  void clearsTheReachLossWhenEveryServerIsConnected() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(0, "context7: npx -y @upstash/context7-mcp - ✔ Connected\n", ""));
    Reach reach = mock(Reach.class);

    new ClaudeMcpHealthProbe(
            runner, ClaudeProperties.defaults(), AssistantProperties.empty(), reach)
        .brokenServers();

    verify(reach).reached("claude-mcp", "mcp");
  }

  @Test
  void reportsProbeThatFailedAsUnknownRatherThanAsNothingBeingDown() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(new Processes.Result(1, "", "claude: command not found"));

    var broken =
        new ClaudeMcpHealthProbe(
                runner, ClaudeProperties.defaults(), AssistantProperties.empty(), mock(Reach.class))
            .brokenServers();

    assertThat(broken).isEmpty();
  }

  @Test
  void reportsOutputThatNamesNoServerAsUnknownRatherThanAsNothingBeingDown() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0, "No MCP servers configured. Use `claude mcp add` to add one.", ""));

    var broken =
        new ClaudeMcpHealthProbe(
                runner, ClaudeProperties.defaults(), AssistantProperties.empty(), mock(Reach.class))
            .brokenServers();

    assertThat(broken).isEmpty();
  }

  @Test
  void namesTheLoginDeclaredServerNeedsWhereItCannotJudgeOne() {
    try (FakeLogCapture log = new FakeLogCapture(ClaudeMcpHealthProbe.class)) {
      new ClaudeMcpHealthProbe(
              mock(ProcessRunner.class),
              ClaudeProperties.defaults(),
              AssistantProperties.empty().withMcpConfig("/opt/acme/servers.json"),
              mock(Reach.class))
          .brokenServers();

      assertThat(log.events())
          .filteredOn(event -> "mcp probe skipped".equals(event.getMessage()))
          .flatExtracting(ILoggingEvent::getKeyValuePairs)
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains(
              "fix=claude --strict-mcp-config --mcp-config /opt/acme/servers.json"
                  + " --setting-sources user,project,local, then /mcp: a declared server carries"
                  + " its own login");
    }
  }

  @Test
  void refusesToJudgeServersThatAreDeclaredRatherThanResolvedByTheCli() {
    ProcessRunner runner = mock(ProcessRunner.class);

    var broken =
        new ClaudeMcpHealthProbe(
                runner,
                ClaudeProperties.defaults(),
                AssistantProperties.empty()
                    .withMcpConfig("{\"mcpServers\":{\"acme\":{\"command\":\"x\"}}}"),
                mock(Reach.class))
            .brokenServers();

    assertThat(broken).isEmpty();
    verifyNoInteractions(runner);
  }

  @Test
  void listsEveryServerTheCliKnowsByNameConnectedOrNot() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                context7: npx -y @upstash/context7-mcp - ✔ Connected
                acme tracker: https://mcp.example.com/mcp (HTTP) - ! Needs authentication
                """,
                ""));

    var servers =
        new ClaudeMcpHealthProbe(
                runner, ClaudeProperties.defaults(), AssistantProperties.empty(), mock(Reach.class))
            .servers();

    assertThat(servers).contains(List.of("context7", "acme tracker"));
  }

  @Test
  void readsTheVerdictOfServerWhoseFailureDetailItselfHoldsDash() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                acme tracker: https://mcp.example.com/mcp (HTTP) - ✘ Failed to connect — HTTP 401 - unauthorized
                """,
                ""));

    var broken =
        new ClaudeMcpHealthProbe(
                runner, ClaudeProperties.defaults(), AssistantProperties.empty(), mock(Reach.class))
            .brokenServers();

    assertThat(broken).contains(List.of("acme tracker (Failed to connect)"));
  }

  @Test
  void asksTheCliOnlyOnceWhileItsAnswerIsFresh() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                context7: npx -y @upstash/context7-mcp - ✔ Connected
                """,
                ""));
    var probe =
        new ClaudeMcpHealthProbe(
            runner, ClaudeProperties.defaults(), AssistantProperties.empty(), mock(Reach.class));

    probe.brokenServers();
    var servers = probe.servers();

    assertThat(servers).contains(List.of("context7"));
    verify(runner, times(1)).run(any(Path.class), any(Duration.class), any());
  }

  @Test
  void listsAllEightBrokenServersWithoutMoreLine() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                s1: cmd - ✘ Failed to connect
                s2: cmd - ✘ Failed to connect
                s3: cmd - ✘ Failed to connect
                s4: cmd - ✘ Failed to connect
                s5: cmd - ✘ Failed to connect
                s6: cmd - ✘ Failed to connect
                s7: cmd - ✘ Failed to connect
                s8: cmd - ✘ Failed to connect
                """,
                ""));

    var broken =
        new ClaudeMcpHealthProbe(
                runner, ClaudeProperties.defaults(), AssistantProperties.empty(), mock(Reach.class))
            .brokenServers();

    assertThat(broken)
        .contains(
            List.of(
                "s1 (Failed to connect)",
                "s2 (Failed to connect)",
                "s3 (Failed to connect)",
                "s4 (Failed to connect)",
                "s5 (Failed to connect)",
                "s6 (Failed to connect)",
                "s7 (Failed to connect)",
                "s8 (Failed to connect)"));
  }

  @Test
  void capsTheBrokenListAtEightAndCountsTheRest() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                s1: cmd - ✘ Failed to connect
                s2: cmd - ✘ Failed to connect
                s3: cmd - ✘ Failed to connect
                s4: cmd - ✘ Failed to connect
                s5: cmd - ✘ Failed to connect
                s6: cmd - ✘ Failed to connect
                s7: cmd - ✘ Failed to connect
                s8: cmd - ✘ Failed to connect
                s9: cmd - ✘ Failed to connect
                s10: cmd - ✘ Failed to connect
                """,
                ""));

    var broken =
        new ClaudeMcpHealthProbe(
                runner, ClaudeProperties.defaults(), AssistantProperties.empty(), mock(Reach.class))
            .brokenServers();

    assertThat(broken)
        .contains(
            List.of(
                "s1 (Failed to connect)",
                "s2 (Failed to connect)",
                "s3 (Failed to connect)",
                "s4 (Failed to connect)",
                "s5 (Failed to connect)",
                "s6 (Failed to connect)",
                "s7 (Failed to connect)",
                "s8 (Failed to connect)",
                "and 2 more"));
  }
}

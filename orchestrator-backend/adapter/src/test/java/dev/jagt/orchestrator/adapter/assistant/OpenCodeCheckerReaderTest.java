package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestOpenCodeGateBuilder.openCodeGate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.agent.opencode.OpenCodeProperties;
import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.gate.ReadScopes;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import dev.jagt.orchestrator.task.acceptance.AcceptanceDecline;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class OpenCodeCheckerReaderTest {

  private static final RoleDefinition ACCEPTANCE =
      RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"));

  @TempDir static Path state;

  private final Processes processes = mock(Processes.class);
  private final ReadScopes scopes = new ReadScopes();
  private final OpenCodeGate gate =
      openCodeGate().withState(state).build(mock(McpHealth.class), scopes);
  private final OpenCodeCheckerReader reader =
      new OpenCodeCheckerReader(
          new HeadlessOpenCode(
              processes,
              new OpenCodeProperties(
                  "opencode",
                  "http://127.0.0.1:8080/v1",
                  "qwen-4b",
                  Map.of("acceptance", "qwen-9b")),
              mock(UsageTracker.class),
              gate),
          new OpenCodeProperties(
              "opencode", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("acceptance", "qwen-9b")),
          List.of(ACCEPTANCE));

  @Test
  void mapsFencedAnswerToTheAcceptanceVerdictItsRowsCarry(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), anyMap(), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"type\":\"text\",\"part\":{\"text\":\"```json\\n"
                    + "{\\\"failure\\\":\\\"\\\",\\\"rows\\\":[{\\\"criterion\\\":\\\"page"
                    + " opens\\\",\\\"status\\\":\\\"met\\\",\\\"evidence\\\":\\\"GET answered"
                    + " 200\\\"}]}\\n"
                    + "```\"}}",
                ""));

    Answer<AcceptanceVerdict> answer =
        reader.read(
            new CheckerReader.Run(
                "",
                "check ABC-42",
                worktree,
                "abc123",
                "",
                new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
                ACCEPTANCE.replies().answer().json(),
                "acceptance"));

    assertThat(answer.facts().orElseThrow().rows())
        .extracting(AcceptanceVerdict.Row::status)
        .containsExactly(AcceptanceVerdict.Status.MET);
  }

  @Test
  void readsOnTheModelTheRoleIsNamedWithAndHoldsItToItsOwnToolsAndEnvironments(
      @TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), anyMap(), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));

    reader.reconsider(
        new CheckerReader.Run(
            "house rules",
            "check ABC-42",
            worktree,
            "abc123",
            "sonnet",
            new CheckerReader.Reach(
                List.of("https://dev.example.test"), List.of(), Optional.empty()),
            ACCEPTANCE.replies().decline().json(),
            "acceptance"));

    ArgumentCaptor<Map<String, String>> env = ArgumentCaptor.captor();
    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(any(Path.class), any(Duration.class), env.capture(), command.capture());
    assertThat(env.getValue().get("OPENCODE_CONFIG_CONTENT"))
        .contains("local/qwen-9b", "\"git log*\":\"allow\"", "\"webfetch\":\"allow\"");
    assertThat(command.getValue().getLast()).startsWith("house rules\n\ncheck ABC-42");
  }

  @Test
  void failsTheCheckWhereTheRunDoesNotReturn(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), anyMap(), anyList()))
        .thenThrow(new IllegalStateException("Command timed out"));

    Answer<AcceptanceDecline> answer =
        reader.reconsider(
            new CheckerReader.Run(
                "",
                "check ABC-42",
                worktree,
                "abc123",
                "",
                new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
                ACCEPTANCE.replies().decline().json(),
                "acceptance"));

    assertThat(answer.facts().orElseThrow().failure())
        .startsWith("the check failed: did not return");
  }
}

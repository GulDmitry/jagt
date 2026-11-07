package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.agent.HookEndpoint;
import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ReadScopes;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaVerdict;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class OpenCodeCheckerReaderTest {

  private static final RoleDefinition QA =
      RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist"));

  @TempDir static Path state;

  private final Processes processes = mock(Processes.class);
  private final ReadScopes scopes = new ReadScopes();
  private final OpenCodeGate gate =
      new OpenCodeGate(
          new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(state.toString())),
          new ReadOnlyTools(
              mock(McpHealth.class),
              AssistantProperties.empty(),
              scopes,
              new HookEndpoint(
                  "http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent")),
          new HookEndpoint(
              "http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));
  private final OpenCodeCheckerReader reader =
      new OpenCodeCheckerReader(
          new HeadlessOpenCode(
              processes,
              new OpenCodeProperties(
                  "opencode", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("qa", "qwen-9b")),
              mock(UsageTracker.class),
              gate),
          new OpenCodeProperties(
              "opencode", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("qa", "qwen-9b")),
          List.of(QA));

  @Test
  void mapsAFencedAnswerToTheQaVerdictItsRowsCarry(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"type\":\"text\",\"part\":{\"text\":\"```json\\n"
                    + "{\\\"failure\\\":\\\"\\\",\\\"rows\\\":[{\\\"criterion\\\":\\\"page"
                    + " opens\\\",\\\"status\\\":\\\"met\\\",\\\"evidence\\\":\\\"GET answered"
                    + " 200\\\"}]}\\n"
                    + "```\"}}",
                ""));

    Answer<QaVerdict> answer =
        reader.read(
            new CheckerReader.Run(
                "",
                "check ABC-42",
                worktree,
                "abc123",
                "",
                List.of(),
                QA.replies().answer().json(),
                "qa"));

    assertThat(answer.facts().orElseThrow().rows())
        .extracting(QaVerdict.Row::status)
        .containsExactly(QaVerdict.Status.MET);
  }

  @Test
  void readsOnTheModelTheRoleIsNamedWithAndHoldsItToItsOwnToolsAndEnvironments(
      @TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenReturn(new Processes.Result(0, "", ""));

    reader.reconsider(
        new CheckerReader.Run(
            "house rules",
            "check ABC-42",
            worktree,
            "abc123",
            "sonnet",
            List.of("https://dev.example.test"),
            QA.replies().decline().json(),
            "qa"));

    ArgumentCaptor<Map<String, String>> env = ArgumentCaptor.captor();
    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(any(Path.class), any(Duration.class), env.capture(), command.capture());
    assertThat(env.getValue().get("OPENCODE_CONFIG_CONTENT"))
        .contains("local/qwen-9b", "\"git log*\":\"allow\"", "\"webfetch\":\"allow\"");
    assertThat(command.getValue().getLast()).startsWith("house rules\n\ncheck ABC-42");
  }

  @Test
  void failsTheCheckWhereTheRunDoesNotReturn(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenThrow(new IllegalStateException("Command timed out"));

    Answer<QaDecline> answer =
        reader.reconsider(
            new CheckerReader.Run(
                "",
                "check ABC-42",
                worktree,
                "abc123",
                "",
                List.of(),
                QA.replies().decline().json(),
                "qa"));

    assertThat(answer.facts().orElseThrow().failure())
        .startsWith("the check failed: did not return");
  }
}

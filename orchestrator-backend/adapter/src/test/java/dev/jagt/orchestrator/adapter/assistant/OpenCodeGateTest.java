package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.adapter.agent.HookEndpoint;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.service.ReadScopes;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenCodeGateTest {

  private final ReadScopes scopes = new ReadScopes();
  private final HookEndpoint hooks =
      new HookEndpoint(
          "http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent");
  private final ReadOnlyTools tools =
      new ReadOnlyTools(mock(McpHealth.class), AssistantProperties.empty(), scopes, hooks);

  @Test
  void writesThePluginBesideStateJsonAndReplacesWhatAnEarlierStartLeft(@TempDir Path root)
      throws IOException {
    Files.createDirectories(root.resolve("opencode-gate"));
    Files.writeString(root.resolve("opencode-gate/index.js"), "stale");

    OpenCodeGate gate =
        new OpenCodeGate(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())),
            tools,
            hooks);

    assertThat(gate.directory()).isEqualTo(root.resolve("opencode-gate"));
    assertThat(Files.readString(gate.directory().resolve("index.js")))
        .contains("id: \"jagt-gate\"", "JAGT_READ_GATE");
    assertThat(Files.readString(gate.directory().resolve("package.json")))
        .contains("\"main\":\"index.js\"");
  }

  @Test
  void namesTheFencesUrlToThePluginAndHoldsTheScopeUnderIt(@TempDir Path root) {
    OpenCodeGate gate =
        new OpenCodeGate(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())),
            tools,
            hooks);

    var env = gate.open("fence-1", new ReadScope(List.of(root), List.of(), true));

    assertThat(env).containsEntry("JAGT_READ_GATE", "http://127.0.0.1:8290/api/agent/read/fence-1");
    assertThat(scopes.ask("fence-1")).isPresent();
  }

  @Test
  void hearsACallOnlyOnceTheGateWasAsked(@TempDir Path root) {
    OpenCodeGate gate =
        new OpenCodeGate(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())),
            tools,
            hooks);
    gate.open("fence-1", new ReadScope(List.of(root), List.of(), true));
    boolean before = gate.heard("fence-1");

    scopes.hear("fence-1", "Bash", "git log");

    assertThat(before).isFalse();
    assertThat(gate.heard("fence-1")).isTrue();
  }
}

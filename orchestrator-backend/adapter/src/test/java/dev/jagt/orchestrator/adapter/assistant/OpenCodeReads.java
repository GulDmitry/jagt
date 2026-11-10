package dev.jagt.orchestrator.adapter.assistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.agent.HookEndpoint;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ReadScopes;
import dev.jagt.orchestrator.task.AssistantCallKind;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class OpenCodeReads {

  private OpenCodeReads() {}

  static OpenCodeGate gate(Path state, ReadScopes scopes) {
    return new OpenCodeGate(
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(state.toString())),
        new ReadOnlyTools(
            mock(McpHealth.class),
            AssistantProperties.empty(),
            scopes,
            new HookEndpoint(
                "http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent")),
        new HookEndpoint(
            "http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));
  }

  static void answeringAfterHearing(
      Processes processes, ReadScopes scopes, String tool, String detail, Processes.Result result) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenAnswer(
            call -> {
              String url = call.<Map<String, String>>getArgument(2).get("JAGT_READ_GATE");
              scopes.hear(
                  Objects.requireNonNull(url)
                      .substring(Objects.requireNonNull(url).lastIndexOf('/') + 1),
                  tool,
                  detail);
              return result;
            });
  }

  static OpenCodeRead countingCommits(Path worktree, String schema) {
    return new OpenCodeRead(
        worktree,
        "count the commits",
        schema,
        "qwen-4b",
        OpenCodeScope.of(
            List.of("git log*"), List.of(), List.of(), false, true, List.of(worktree), List.of()),
        AssistantCallKind.ACCEPTANCE_CHECK,
        Duration.ofMinutes(1));
  }

  static OpenCodeRead withoutShell(Path worktree, String schema) {
    return new OpenCodeRead(
        worktree,
        "count the commits",
        schema,
        "qwen-4b",
        OpenCodeScope.of(
            List.of(),
            List.of(),
            List.of("**/review_replies.md"),
            false,
            false,
            List.of(worktree),
            List.of()),
        AssistantCallKind.ACCEPTANCE_CHECK,
        Duration.ofMinutes(1));
  }
}

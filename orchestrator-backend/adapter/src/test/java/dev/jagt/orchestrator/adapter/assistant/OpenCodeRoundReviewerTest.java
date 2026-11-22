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
import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.gate.ReadScopes;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class OpenCodeRoundReviewerTest {

  @TempDir static Path state;

  private final Processes processes = mock(Processes.class);
  private final ReadScopes scopes = new ReadScopes();
  private final OpenCodeGate gate =
      openCodeGate().withState(state).build(mock(McpHealth.class), scopes);
  private final OpenCodeRoundReviewer reviewer =
      new OpenCodeRoundReviewer(
          new HeadlessOpenCode(
              processes,
              new OpenCodeProperties(
                  "opencode", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("master", "")),
              mock(UsageTracker.class),
              gate),
          new OpenCodeProperties(
              "opencode", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("master", "")));

  @Test
  void mapsFencedAnswerToTheJudgementItsVerdictAndFindingsCarry(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), anyMap(), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"type\":\"text\",\"part\":{\"text\":\"```json\\n"
                    + "{\\\"failure\\\":\\\"\\\",\\\"verdict\\\":\\\"not"
                    + " ready\\\",\\\"findings\\\":[{\\\"file\\\":\\\"A.java\\\",\\\"issue\\\":\\\""
                    + "unguarded\\\",\\\"pattern\\\":\\\"p\\\",\\\"severity\\\":\\\"Wrong\\\",\\\"a"
                    + "nchor\\\":\\\"a\\\",\\\"kind\\\":\\\"behaviour\\\"}]}\\n"
                    + "```\"}}",
                ""));

    Answer<RoundReviewer.Judgement> answer =
        reviewer.review(new RoundReviewer.Round("", "review ABC-42", List.of(worktree), "opus"));

    assertThat(answer.facts().orElseThrow().verdict()).isEqualTo("not ready");
    assertThat(answer.facts().orElseThrow().findings())
        .extracting(RoundReviewer.Finding::severity)
        .containsExactly("wrong");
  }

  @Test
  void readsOnTheDefaultModelWhereMasterIsNamedWithBlankOneAndRunsNoBuild(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), anyMap(), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));

    reviewer.review(new RoundReviewer.Round("", "review ABC-42", List.of(worktree), "opus"));

    ArgumentCaptor<Map<String, String>> env = ArgumentCaptor.captor();
    verify(processes).run(any(Path.class), any(Duration.class), env.capture(), anyList());
    assertThat(env.getValue().get("OPENCODE_CONFIG_CONTENT"))
        .contains(
            "local/qwen-4b",
            "\"git diff*\":\"allow\"",
            "\"./gradlew*\":\"deny\"",
            "\"webfetch\":\"deny\"");
    assertThat(reviewer.loadsMcpServer()).isFalse();
  }
}

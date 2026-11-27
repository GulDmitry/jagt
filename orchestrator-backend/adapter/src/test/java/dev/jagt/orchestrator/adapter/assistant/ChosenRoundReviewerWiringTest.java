package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestHeadlessClaudeBuilder.headlessClaude;
import static dev.jagt.orchestrator.adapter.assistant.TestOpenCodeGateBuilder.openCodeGate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.adapter.agent.opencode.OpenCodeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.gate.ReadScopes;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChosenRoundReviewerWiringTest {

  static Stream<Arguments> readers() {
    return Stream.of(
        Arguments.of(
            OpenCodeProperties.defaults(),
            "{\"structured_output\":{\"failure\":\"\",\"verdict\":\"ready\",\"findings\":[]}}"),
        Arguments.of(
            new OpenCodeProperties(
                "opencode", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("master", "")),
            "{\"type\":\"text\",\"part\":{\"text\":\"```json\\n"
                + "{\\\"failure\\\":\\\"\\\",\\\"verdict\\\":\\\"ready\\\","
                + "\\\"findings\\\":[]}\\n```\"}}"));
  }

  @ParameterizedTest
  @MethodSource("readers")
  void roundIsJudgedOnWhicheverCliTheReadsChoose(
      OpenCodeProperties reads, String stdout, @TempDir Path worktree, @TempDir Path state) {
    FakeProcesses runner = new FakeProcesses().answering(new Processes.Result(0, stdout, ""));
    var chosen =
        new ChosenRoundReviewer(
            new HeadlessClaudeRoundReviewer(
                headlessClaude().build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class)),
                ClaudeProperties.defaults(),
                AssistantProperties.empty()),
            new OpenCodeRoundReviewer(
                new HeadlessOpenCode(
                    runner,
                    reads,
                    mock(UsageTracker.class),
                    openCodeGate().withState(state).build(mock(McpHealth.class), new ReadScopes())),
                reads),
            reads);

    RoundReviewer.Judgement judgement =
        chosen
            .review(new RoundReviewer.Round("", "review ABC-42", List.of(worktree), ""))
            .facts()
            .orElseThrow();

    assertThat(judgement.verdict()).isEqualTo(RoundReviewer.Verdict.READY);
  }
}

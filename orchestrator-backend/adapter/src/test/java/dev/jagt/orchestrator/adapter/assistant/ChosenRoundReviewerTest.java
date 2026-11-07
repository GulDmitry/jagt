package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChosenRoundReviewerTest {

  private final HeadlessClaudeRoundReviewer claude = mock(HeadlessClaudeRoundReviewer.class);
  private final OpenCodeRoundReviewer opencode = mock(OpenCodeRoundReviewer.class);

  @Test
  void reviewsOnOpenCodeWhereMasterIsNamedUnderReads() {
    RoundReviewer.Round round =
        new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/wt")), "");
    Answer<RoundReviewer.Judgement> local =
        new Answer<>(Optional.of(RoundReviewer.Judgement.failed("local")), TokenUsage.NONE);
    when(opencode.review(round)).thenReturn(local);

    var chosen =
        new ChosenRoundReviewer(
            claude,
            opencode,
            new OpenCodeProperties(
                "", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("master", "")));

    assertThat(chosen.review(round)).isSameAs(local);
  }

  @Test
  void reviewsOnClaudeAndLoadsItsMcpServersWhereMasterIsNotNamed() {
    RoundReviewer.Round round =
        new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/wt")), "");
    Answer<RoundReviewer.Judgement> cloud =
        new Answer<>(Optional.of(RoundReviewer.Judgement.failed("cloud")), TokenUsage.NONE);
    when(claude.review(round)).thenReturn(cloud);
    when(claude.loadsMcpServer()).thenReturn(true);

    var chosen =
        new ChosenRoundReviewer(
            claude,
            opencode,
            new OpenCodeProperties("", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("qa", "")));

    assertThat(chosen.review(round)).isSameAs(cloud);
    assertThat(chosen.loadsMcpServer()).isTrue();
  }
}

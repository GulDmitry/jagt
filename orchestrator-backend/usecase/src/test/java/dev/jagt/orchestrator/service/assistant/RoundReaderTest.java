package dev.jagt.orchestrator.service.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Round;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoundReaderTest {

  private final Assistants assistants = mock(Assistants.class, RETURNS_DEEP_STUBS);
  private final Round round = new Round("", "judge it", List.of(), "");
  private final Judgement unread =
      new Judgement("", Verdict.UNREAD, List.of(), "", List.of(), "", Press.NONE);
  private final Judgement ready =
      new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE);

  @Test
  void asksOnceMoreTellingTheVerdictFieldWhereTheWordIsOutsideTheVocabulary() {
    when(assistants.reviewer().review(any()))
        .thenReturn(new Answer<>(Optional.of(unread), TokenUsage.ofCall(1_000, 0, 10, 0.01)))
        .thenReturn(new Answer<>(Optional.of(ready), TokenUsage.ofCall(1_000, 0, 10, 0.01)));

    Answer<Judgement> read = new RoundReader(assistants).read(round);

    ArgumentCaptor<Round> asked = ArgumentCaptor.forClass(Round.class);
    verify(assistants.reviewer(), times(2)).review(asked.capture());
    assertThat(asked.getAllValues().get(1).prompt()).contains("judge it", "verdict");
    assertThat(read.facts()).contains(ready);
    assertThat(read.usage().costUsd()).isEqualTo(0.02);
  }

  @Test
  void failsTheJudgementNamingTheVerdictWhereTheSecondAnswerIsOutsideTheVocabularyToo() {
    when(assistants.reviewer().review(any()))
        .thenReturn(new Answer<>(Optional.of(unread), TokenUsage.NONE));

    Answer<Judgement> read = new RoundReader(assistants).read(round);

    verify(assistants.reviewer(), times(2)).review(any());
    assertThat(read.facts().orElseThrow().failure()).contains("verdict");
  }

  @Test
  void asksOnceWhereTheAnswerIsRead() {
    when(assistants.reviewer().review(any()))
        .thenReturn(new Answer<>(Optional.of(ready), TokenUsage.NONE));

    new RoundReader(assistants).read(round);

    verify(assistants.reviewer(), times(1)).review(any());
  }
}

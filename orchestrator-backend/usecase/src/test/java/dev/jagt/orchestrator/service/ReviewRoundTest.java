package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewRoundTest {

  private final ReviewReader reader = mock(ReviewReader.class);
  private final PipelineRetry host = mock(PipelineRetry.class);
  private final ReviewRound round = new ReviewRound(reader, host);

  @Test
  void readsTheSameRoundTwiceWhereTheModelWroteOtherNoteIdsButTheHostGivesTheSame() {
    when(reader.read("ABC-1", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 7),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(
                            List.of("rename x"), List.of(), List.of("101187")))),
                TokenUsage.NONE))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 7),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(
                            List.of("rename x"), List.of(), List.of("101219")))),
                TokenUsage.NONE));
    when(host.unresolvedNotes("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of(List.of("301")), TokenUsage.NONE));

    ReviewFacts first = round.read("ABC-1", "proj", "http://mr/1").facts().orElseThrow();
    ReviewFacts second = round.read("ABC-1", "proj", "http://mr/1").facts().orElseThrow();

    assertThat(first.noteKey().sameAs(second.noteKey())).isTrue();
    assertThat(first.discussion().newestNotes()).containsExactly("301");
  }

  @Test
  void carriesNoNoteIdWhereTheHostCouldNotBeAsked() {
    when(reader.read("ABC-1", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 7),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(
                            List.of("rename x"), List.of(), List.of("101187")))),
                TokenUsage.NONE));
    when(host.unresolvedNotes("proj", "http://mr/1"))
        .thenReturn(Answer.failed(TokenUsage.NONE, "gh exited 1"));

    ReviewFacts facts = round.read("ABC-1", "proj", "http://mr/1").facts().orElseThrow();

    assertThat(facts.discussion().newestNotes()).isEmpty();
  }

  @Test
  void asksTheHostNothingOfARequestTheModelFoundMissing() {
    when(reader.read("ABC-1", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(false, false, false, false, 0),
                        new ReviewFacts.Pipeline("", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    round.read("ABC-1", "proj", "http://mr/1");

    verifyNoInteractions(host);
  }
}

package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CodeHost;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ReviewRoundTest {

  private final ReviewReader reader = mock(ReviewReader.class);
  private final CodeHost host = mock(CodeHost.class);
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
    when(host.requestFingerprint("proj", "http://mr/1")).thenReturn(Answer.unavailable());
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
    when(host.requestFingerprint("proj", "http://mr/1")).thenReturn(Answer.unavailable());
    when(host.unresolvedNotes("proj", "http://mr/1"))
        .thenReturn(Answer.failed(TokenUsage.NONE, "gh exited 1"));

    ReviewFacts facts = round.read("ABC-1", "proj", "http://mr/1").facts().orElseThrow();

    assertThat(facts.discussion().newestNotes()).isEmpty();
  }

  @Test
  void asksTheHostForNoNotesOfARequestTheModelFoundMissing() {
    when(reader.read("ABC-1", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(false, false, false, false, 0),
                        new ReviewFacts.Pipeline("", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(host.requestFingerprint("proj", "http://mr/1")).thenReturn(Answer.unavailable());

    round.read("ABC-1", "proj", "http://mr/1");

    verify(host, never()).unresolvedNotes("proj", "http://mr/1");
  }

  @Test
  void keepsTheLastVerdictAndPaysForNoReadWhenAPollFindsTheRequestUnchanged() {
    when(reader.read("ABC-1", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, true, false, false, 7),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                new TokenUsage(1, 900, 0, 40, 0.06)));
    when(host.requestFingerprint("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of("OPEN 2026-11-09T08:00:00Z abc123"), TokenUsage.NONE));
    when(host.unresolvedNotes("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of(List.of()), TokenUsage.NONE));

    OriginContext.as(ActionOrigin.AUTO_REVIEW, () -> round.read("ABC-1", "proj", "http://mr/1"));
    Answer<ReviewFacts> second =
        OriginContext.as(
            ActionOrigin.AUTO_REVIEW, () -> round.read("ABC-1", "proj", "http://mr/1"));

    verify(reader, times(1)).read("ABC-1", "http://mr/1");
    assertThat(second.facts().orElseThrow().request().approved()).isTrue();
    assertThat(second.usage()).isEqualTo(TokenUsage.NONE);
  }

  @Test
  void readsTheRequestAgainWhenAPollFindsItsFingerprintChanged() {
    when(reader.read("ABC-1", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 7),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(host.requestFingerprint("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of("OPEN 2026-11-09T08:00:00Z abc123"), TokenUsage.NONE))
        .thenReturn(new Answer<>(Optional.of("OPEN 2026-11-09T08:30:00Z abc123"), TokenUsage.NONE));
    when(host.unresolvedNotes("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of(List.of()), TokenUsage.NONE));

    OriginContext.as(ActionOrigin.AUTO_REVIEW, () -> round.read("ABC-1", "proj", "http://mr/1"));
    OriginContext.as(ActionOrigin.AUTO_REVIEW, () -> round.read("ABC-1", "proj", "http://mr/1"));

    verify(reader, times(2)).read("ABC-1", "http://mr/1");
  }

  @Test
  void readsTheRequestAgainWhenAPollFindsANewNoteUnderAnUnchangedFingerprint() {
    when(reader.read("ABC-1", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 7),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(host.requestFingerprint("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of("OPEN 2026-11-09T08:00:00Z abc123"), TokenUsage.NONE));
    when(host.unresolvedNotes("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of(List.of()), TokenUsage.NONE))
        .thenReturn(new Answer<>(Optional.of(List.of("301")), TokenUsage.NONE));

    OriginContext.as(ActionOrigin.AUTO_REVIEW, () -> round.read("ABC-1", "proj", "http://mr/1"));
    OriginContext.as(ActionOrigin.AUTO_REVIEW, () -> round.read("ABC-1", "proj", "http://mr/1"));

    verify(reader, times(2)).read("ABC-1", "http://mr/1");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void readsTheRequestOnEveryPollWhereTheHostGivesNoFingerprint(String fingerprint) {
    when(reader.read("ABC-1", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 7),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(host.requestFingerprint("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.ofNullable(fingerprint), TokenUsage.NONE));
    when(host.unresolvedNotes("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of(List.of()), TokenUsage.NONE));

    OriginContext.as(ActionOrigin.AUTO_REVIEW, () -> round.read("ABC-1", "proj", "http://mr/1"));
    OriginContext.as(ActionOrigin.AUTO_REVIEW, () -> round.read("ABC-1", "proj", "http://mr/1"));

    verify(reader, times(2)).read("ABC-1", "http://mr/1");
  }

  @Test
  void readsAnUnchangedRequestAgainWhenAHumanAsksForTheSweep() {
    when(reader.read("ABC-1", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 7),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(host.requestFingerprint("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of("OPEN 2026-11-09T08:00:00Z abc123"), TokenUsage.NONE));
    when(host.unresolvedNotes("proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of(List.of()), TokenUsage.NONE));

    OriginContext.as(ActionOrigin.AUTO_REVIEW, () -> round.read("ABC-1", "proj", "http://mr/1"));
    OriginContext.as(ActionOrigin.BOARD, () -> round.read("ABC-1", "proj", "http://mr/1"));

    verify(reader, times(2)).read("ABC-1", "http://mr/1");
  }
}

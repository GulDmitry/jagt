package dev.jagt.orchestrator.service.codereview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.service.codereview.CodeReviewSettings.Settings;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CodeReviewRoundTest {

  private final RequestNotes notes = mock(RequestNotes.class);
  private final ReviewLedger ledger = mock(ReviewLedger.class);
  private final CodeReviewPanel panel = mock(CodeReviewPanel.class);
  private final SubjectCheckout checkout = mock(SubjectCheckout.class);
  private final NotePoster poster = mock(NotePoster.class);
  private final CodeReviewRound round = new CodeReviewRound(notes, ledger, panel, checkout, poster);
  private final Subject other = new Subject("shop", "https://h/a/b/7", "git@h:a/b.git", "");
  private final LedgerRow fourReviews =
      LedgerRow.of(other.url(), "shop", "").withHeads(List.of("a", "b", "c", "d"));

  @Test
  void requestOfAnotherPersonIsNotReadAndNoModelAskedWhenPostingOthersIsOff() {
    round.run(other, Settings.defaults(), "");

    verifyNoInteractions(notes, panel, checkout, poster);
  }

  @Test
  void newHeadOfA1200LineRequestWithFourReviewsIsNotReviewedAgain() {
    when(ledger.row(other.url())).thenReturn(Optional.of(fourReviews));
    when(notes.change("shop", other.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    RequestChange.defaults().withHeadSha("e").withDiff("+x\n".repeat(1200))),
                TokenUsage.NONE));

    round.run(other, new Settings(true, true, true, 5000), "");

    verifyNoInteractions(checkout, panel);
  }

  @Test
  void newHeadOfA2000LineRequestWithFourReviewsIsStillReviewed() {
    when(ledger.row(other.url())).thenReturn(Optional.of(fourReviews));
    when(notes.change("shop", other.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    RequestChange.defaults().withHeadSha("e").withDiff("+x\n".repeat(2000))),
                TokenUsage.NONE));
    when(checkout.open(any(), any())).thenReturn(Optional.empty());

    round.run(other, new Settings(true, true, true, 5000), "");

    verify(checkout).open(other, "e");
  }

  @Test
  void headAlreadyReviewedIsNotReadAgain() {
    when(ledger.row(other.url()))
        .thenReturn(Optional.of(LedgerRow.of(other.url(), "shop", "").withHeads(List.of("e"))));
    when(notes.change("shop", other.url()))
        .thenReturn(
            new Answer<>(Optional.of(RequestChange.defaults().withHeadSha("e")), TokenUsage.NONE));

    round.run(other, new Settings(true, true, true, 5000), "");

    verifyNoInteractions(checkout, panel);
  }

  @Test
  void diffPastTheLimitIsSkippedAndItsHeadCountsAsReviewed() {
    when(ledger.row(other.url())).thenReturn(Optional.empty());
    when(notes.change("shop", other.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(RequestChange.defaults().withHeadSha("e").withDiff("+x\n+y\n+z\n")),
                TokenUsage.NONE));

    round.run(other, new Settings(true, true, true, 2), "");

    verify(ledger).update(any(), any(), any(), any());
    verifyNoInteractions(checkout, panel);
    assertThat(CodeReviewRound.allowed(RequestChange.defaults().withDiff("+x\n"))).isEqualTo(2);
  }
}

package dev.jagt.orchestrator.job.codereview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.job.codereview.request.LedgerRow;
import dev.jagt.orchestrator.job.codereview.request.NotePoster;
import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.port.codehost.review.NoteAnchor;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
import dev.jagt.orchestrator.service.CodeReviewSettings.Settings;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void requestOfJagtTaskIsNeverReadNorNotedWhateverTheSettings(boolean postOthers) {
    Subject own = new Subject("shop", "https://h/a/b/8", "git@h:a/b.git", "ABC-42");

    round.run(own, new Settings(true, postOthers, 5000, 2.55), "");

    verifyNoInteractions(ledger, notes, panel, checkout, poster);
  }

  @Test
  void changeReadThatFailedIsHandedToThePanelAsTheJobsFailure() {
    Answer<RequestChange> failed = Answer.failed(TokenUsage.NONE, "the host refused");
    when(notes.change("shop", other.url())).thenReturn(failed);

    round.run(other, new Settings(true, true, 5000, 2.55), "");

    verify(panel).unread(failed);
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

    round.run(other, new Settings(true, true, 5000, 2.55), "");

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

    round.run(other, new Settings(true, true, 5000, 2.55), "");

    verify(checkout).open(other, "e");
  }

  @Test
  void headAlreadyReviewedIsNotReadAgain() {
    when(ledger.row(other.url()))
        .thenReturn(Optional.of(LedgerRow.of(other.url(), "shop", "").withHeads(List.of("e"))));
    when(notes.change("shop", other.url()))
        .thenReturn(
            new Answer<>(Optional.of(RequestChange.defaults().withHeadSha("e")), TokenUsage.NONE));

    round.run(other, new Settings(true, true, 5000, 2.55), "");

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

    round.run(other, new Settings(true, true, 2, 2.55), "");

    verify(ledger).update(any(), any(), any(), any());
    verifyNoInteractions(checkout, panel);
    assertThat(CodeReviewRound.allowed(RequestChange.defaults().withDiff("+x\n"))).isEqualTo(2);
  }

  @Test
  void mergedRequestDropsItsJudgementUnreadAndUnposted() {
    when(ledger.row(other.url())).thenReturn(Optional.empty());
    when(notes.change("shop", other.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    RequestChange.defaults().withHeadSha("e").withState(RequestState.MERGED)),
                TokenUsage.NONE));

    round.run(other, new Settings(true, true, 5000, 2.55), "");

    verifyNoInteractions(checkout, panel, poster);
  }

  @Test
  void closedRequestDropsItsJudgementUnreadAndUnposted() {
    when(ledger.row(other.url())).thenReturn(Optional.empty());
    when(notes.change("shop", other.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    RequestChange.defaults().withHeadSha("e").withState(RequestState.CLOSED)),
                TokenUsage.NONE));

    round.run(other, new Settings(true, true, 5000, 2.55), "");

    verifyNoInteractions(checkout, panel, poster);
  }

  @Test
  void thirdPartyRequestWhoseReadsCostTheCeilingAsksNoModel() {
    when(ledger.row(other.url()))
        .thenReturn(Optional.of(LedgerRow.of(other.url(), "shop", "").withCostUsd(2.6)));

    round.run(other, new Settings(true, true, 5000, 2.55), "");

    verifyNoInteractions(notes, checkout, panel, poster);
  }

  @Test
  void findingNamingFileAndLineIsPostedOnThatLine() {
    String diff =
        "--- a/A.java\n+++ b/A.java\n@@ -1,2 +10,3 @@\n keep\n+int x = null;\n+int y = 2;\n";
    when(ledger.row(other.url())).thenReturn(Optional.empty());
    when(notes.change("shop", other.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(RequestChange.defaults().withHeadSha("e").withDiff(diff)),
                TokenUsage.NONE));
    SubjectCheckout.Open open = new SubjectCheckout.Open(java.nio.file.Path.of("/c"), () -> {});
    when(checkout.open(any(), any())).thenReturn(Optional.of(open));
    when(panel.review(any(), any(), any(), any()))
        .thenReturn(
            new RoundReviewer.Judgement(
                "",
                Verdict.NOT_READY,
                List.of(
                    new RoundReviewer.Finding(
                        "A.java", "null here", "p", "wrong", "int x = null;", "npe")),
                "",
                List.of(),
                "",
                Press.NONE));
    when(poster.post(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(NotePoster.Outcome.POSTED);

    round.run(other, new Settings(true, true, 5000, 2.55), "");

    verify(poster)
        .post(
            eq(other), eq("e"), any(), any(), any(), eq(""), eq(new NoteAnchor("A.java", 11, "e")));
  }
}

package dev.jagt.orchestrator.job.codereview;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.job.codereview.request.LedgerRow;
import dev.jagt.orchestrator.job.codereview.request.NotePoster;
import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.service.codereview.CodeReviewSettings;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BotAnswersTest {

  private final RequestNotes notes = mock(RequestNotes.class);
  private final ReviewLedger ledger = mock(ReviewLedger.class);
  private final CodeReviewPanel panel = mock(CodeReviewPanel.class);
  private final SubjectCheckout checkout = mock(SubjectCheckout.class);
  private final NotePoster poster = mock(NotePoster.class);
  private final BotAnswers answers = new BotAnswers(notes, ledger, panel, checkout, poster);
  private final Subject subject = new Subject("shop", "https://h/a/b/7", "git@h:a/b.git", "t1");

  @Test
  void botThatAnswersFourthTimeGetsNothingPosted() {
    when(notes.notes("shop", subject.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    List.of(
                        RequestNote.defaults()
                            .withId("5")
                            .withThread("T1")
                            .withKind(AuthorKind.BOT))),
                TokenUsage.NONE));
    when(ledger.row(subject.url()))
        .thenReturn(
            Optional.of(LedgerRow.of(subject.url(), "shop", "t1").withAnswers(Map.of("T1", 3))));

    answers.answer(subject, CodeReviewSettings.Settings.defaults(), "");

    verifyNoInteractions(panel, poster, checkout);
  }

  @Test
  void threadWhoseNewestNoteIsTheReviewersOwnGetsNothingPosted() {
    when(notes.notes("shop", subject.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    List.of(
                        RequestNote.defaults()
                            .withId("5")
                            .withThread("T1")
                            .withKind(AuthorKind.BOT),
                        RequestNote.defaults()
                            .withId("6")
                            .withThread("T1")
                            .withKind(AuthorKind.ACTOR))),
                TokenUsage.NONE));
    when(ledger.row(subject.url())).thenReturn(Optional.empty());

    answers.answer(subject, CodeReviewSettings.Settings.defaults(), "");

    verifyNoInteractions(panel, poster, checkout);
  }

  @Test
  void requestWhoseThirdPartyReadsCostTheCeilingIsNotAnswered() {
    when(ledger.row(subject.url()))
        .thenReturn(Optional.of(LedgerRow.of(subject.url(), "shop", "t1").withCostUsd(3.0)));

    answers.answer(subject, CodeReviewSettings.Settings.defaults(), "");

    verifyNoInteractions(notes, panel, poster, checkout);
  }
}

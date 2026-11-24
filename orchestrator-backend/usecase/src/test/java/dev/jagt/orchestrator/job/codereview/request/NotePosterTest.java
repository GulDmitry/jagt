package dev.jagt.orchestrator.job.codereview.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.NoteAnchor;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
import dev.jagt.orchestrator.task.review.AiLine;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotePosterTest {

  private final RequestNotes notes = mock(RequestNotes.class);
  private final ReviewLedger ledger = mock(ReviewLedger.class);
  private final NotePoster poster = new NotePoster(notes, ledger);
  private final Answer<RequestChange> atFirstHead =
      new Answer<>(Optional.of(RequestChange.defaults().withHeadSha("h1")), TokenUsage.NONE);
  private final Subject subject = new Subject("shop", "https://h/a/b/7", "git@h:a/b.git", "");

  @BeforeEach
  void hostHoldsNoNoteAtTheFirstHead() {
    when(notes.change("shop", subject.url())).thenReturn(atFirstHead);
    when(notes.notes("shop", subject.url()))
        .thenReturn(new Answer<>(Optional.of(List.of()), TokenUsage.NONE));
    when(notes.post(any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of("9"), TokenUsage.NONE));
  }

  @Test
  void noteOpensWithTheAiLineAndCarriesItsKey() {
    NotePoster.Outcome outcome = poster.post(subject, "h1", "k1", "Null here", "", NoteAnchor.NONE);

    ArgumentCaptor<NoteDraft> draft = ArgumentCaptor.forClass(NoteDraft.class);
    verify(notes).post(any(), any(), draft.capture());
    assertThat(outcome).isEqualTo(NotePoster.Outcome.POSTED);
    assertThat(draft.getValue().body()).startsWith(AiLine.TEXT).endsWith("<!-- jagt-review:k1 -->");
  }

  @Test
  void noteWhoseKeyTheHostAlreadyHoldsIsNotPostedAgain() {
    when(notes.notes("shop", subject.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(List.of(RequestNote.defaults().withBody("x <!-- jagt-review:k1 -->"))),
                TokenUsage.NONE));

    NotePoster.Outcome outcome = poster.post(subject, "h1", "k1", "Null here", "", NoteAnchor.NONE);

    assertThat(outcome).isEqualTo(NotePoster.Outcome.DUPLICATE);
    verify(notes, never()).post(any(), any(), any());
  }

  @Test
  void judgementIsDroppedUnpostedWhenTheHeadMovedSinceTheRead() {
    when(notes.change("shop", subject.url()))
        .thenReturn(
            new Answer<>(Optional.of(RequestChange.defaults().withHeadSha("h2")), TokenUsage.NONE));

    NotePoster.Outcome outcome = poster.post(subject, "h1", "k1", "Null here", "", NoteAnchor.NONE);

    assertThat(outcome).isEqualTo(NotePoster.Outcome.DROPPED);
    verify(notes, never()).post(any(), any(), any());
  }

  @Test
  void judgementIsDroppedUnpostedWhenTheRequestIsMerged() {
    when(notes.change("shop", subject.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    RequestChange.defaults().withHeadSha("h1").withState(RequestState.MERGED)),
                TokenUsage.NONE));

    NotePoster.Outcome outcome = poster.post(subject, "h1", "k1", "Null here", "", NoteAnchor.NONE);

    assertThat(outcome).isEqualTo(NotePoster.Outcome.DROPPED);
    verify(notes, never()).post(any(), any(), any());
  }

  @Test
  void noteIsPostedOnTheLineItsAnchorNames() {
    poster.post(subject, "h1", "k1", "Null here", "", new NoteAnchor("A.java", 11, "h1"));

    ArgumentCaptor<NoteDraft> draft = ArgumentCaptor.forClass(NoteDraft.class);
    verify(notes).post(any(), any(), draft.capture());
    assertThat(draft.getValue().anchor()).isEqualTo(new NoteAnchor("A.java", 11, "h1"));
  }

  @Test
  void lineTheHostRefusesIsPostedOnTheRequestInstead() {
    when(notes.post(any(), any(), any()))
        .thenReturn(Answer.failed(TokenUsage.NONE, "line is not part of the diff"))
        .thenReturn(new Answer<>(Optional.of("9"), TokenUsage.NONE));

    NotePoster.Outcome outcome =
        poster.post(subject, "h1", "k1", "Null here", "", new NoteAnchor("A.java", 11, "h1"));

    ArgumentCaptor<NoteDraft> draft = ArgumentCaptor.forClass(NoteDraft.class);
    verify(notes, times(2)).post(any(), any(), draft.capture());
    assertThat(outcome).isEqualTo(NotePoster.Outcome.POSTED);
    assertThat(draft.getAllValues().getLast().anchor()).isEqualTo(NoteAnchor.NONE);
  }
}

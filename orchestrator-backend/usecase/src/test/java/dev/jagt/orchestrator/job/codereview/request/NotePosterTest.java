package dev.jagt.orchestrator.job.codereview.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.NoteAnchor;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    NotePoster.Outcome outcome =
        poster.post(subject, "h1", "k1", "t1", "Null here", "", new NoteAnchor("A.java", 11, "h1"));

    ArgumentCaptor<NoteDraft> draft = ArgumentCaptor.forClass(NoteDraft.class);
    verify(notes).post(any(), any(), draft.capture());
    assertThat(outcome).isEqualTo(NotePoster.Outcome.POSTED);
    assertThat(draft.getValue().body())
        .startsWith("AI note: 👍 helps, 👎 does not; reply with what is wrong.")
        .contains("<!-- jagt-review:k1 -->");
  }

  @Test
  void noteWhoseKeyTheHostAlreadyHoldsIsNotPostedAgain() {
    when(notes.notes("shop", subject.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(List.of(RequestNote.defaults().withBody("x <!-- jagt-review:k1 -->"))),
                TokenUsage.NONE));

    NotePoster.Outcome outcome =
        poster.post(subject, "h1", "k1", "t1", "Null here", "", new NoteAnchor("A.java", 11, "h1"));

    assertThat(outcome).isEqualTo(NotePoster.Outcome.DUPLICATE);
    verify(notes, never()).post(any(), any(), any());
  }

  @ParameterizedTest
  @CsvSource({"h2,OPEN", "h1,MERGED"})
  void judgementIsDroppedUnpostedWhenTheRequestMovedOnSinceTheRead(
      String head, RequestState state) {
    when(notes.change("shop", subject.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(RequestChange.defaults().withHeadSha(head).withState(state)),
                TokenUsage.NONE));

    NotePoster.Outcome outcome =
        poster.post(subject, "h1", "k1", "t1", "Null here", "", new NoteAnchor("A.java", 11, "h1"));

    assertThat(outcome).isEqualTo(NotePoster.Outcome.DROPPED);
    verify(notes, never()).post(any(), any(), any());
  }

  @Test
  void noteIsPostedOnTheLineItsAnchorNames() {
    poster.post(subject, "h1", "k1", "t1", "Null here", "", new NoteAnchor("A.java", 11, "h1"));

    ArgumentCaptor<NoteDraft> draft = ArgumentCaptor.forClass(NoteDraft.class);
    verify(notes).post(any(), any(), draft.capture());
    assertThat(draft.getValue().anchor()).isEqualTo(new NoteAnchor("A.java", 11, "h1"));
  }

  @Test
  void lineTheHostRefusesPostsNoNoteOnTheRequest() {
    when(notes.post(any(), any(), any()))
        .thenReturn(Answer.failed(TokenUsage.NONE, "line is not part of the diff"));

    NotePoster.Outcome outcome =
        poster.post(subject, "h1", "k1", "t1", "Null here", "", new NoteAnchor("A.java", 11, "h1"));

    assertThat(outcome).isEqualTo(NotePoster.Outcome.SKIPPED);
    verify(notes, times(1)).post(any(), any(), any());
  }

  @Test
  void findingWithNoDiffLinePostsNoNote() {
    NotePoster.Outcome outcome =
        poster.post(subject, "h1", "k1", "t1", "Title is vague", "", new NoteAnchor("", 0, "h1"));

    assertThat(outcome).isEqualTo(NotePoster.Outcome.SKIPPED);
    verify(notes, never()).post(any(), any(), any());
  }

  @ParameterizedTest
  @CsvSource({"OPEN,DUPLICATE", "RESOLVED,POSTED"})
  void findingIsPostedAgainOnlyWhenItsThreadOfTheRolesOwnIsResolved(
      Resolution thread, NotePoster.Outcome expected) {
    when(notes.notes("shop", subject.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    List.of(
                        RequestNote.defaults()
                            .withKind(AuthorKind.ACTOR)
                            .withResolution(thread)
                            .withBody("x <!-- jagt-thread:t1 -->"))),
                TokenUsage.NONE));

    NotePoster.Outcome outcome =
        poster.post(subject, "h1", "k1", "t1", "Null here", "", new NoteAnchor("A.java", 11, "h1"));

    assertThat(outcome).isEqualTo(expected);
    verify(ledger).update(any(), any(), any(), any());
  }

  @Test
  void replyInsideThreadIsPostedWithoutLine() {
    NotePoster.Outcome outcome =
        poster.post(subject, "h1", "k1", "", "Done", "31", NoteAnchor.NONE);

    ArgumentCaptor<NoteDraft> draft = ArgumentCaptor.forClass(NoteDraft.class);
    verify(notes).post(any(), any(), draft.capture());
    assertThat(outcome).isEqualTo(NotePoster.Outcome.POSTED);
    assertThat(draft.getValue().body()).doesNotContain("jagt-thread");
  }
}

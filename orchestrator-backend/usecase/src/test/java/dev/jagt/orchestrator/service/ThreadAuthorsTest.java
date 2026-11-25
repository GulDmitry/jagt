package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThreadAuthorsTest {

  private final StateService state = mock(StateService.class);
  private final RequestNotes notes = mock(RequestNotes.class);
  private final ThreadAuthors authors = new ThreadAuthors(state, notes);

  @ParameterizedTest
  @CsvSource({
    "review-bot, BOT, Author: bot review-bot",
    "alice, PERSON, Author: person alice",
    "reviewer, PERSON, Author: person reviewer",
    "jagt-account, ACTOR, Author: reviewer jagt-account"
  })
  void givesTheKindTheHostNamesForTheNewestNotesAuthor(
      String author, AuthorKind kind, String expected) {
    when(state.task("ABC-42"))
        .thenReturn(Optional.of(TaskState.builder("shop", "/wt", TaskStatus.CI_POLLING).build()));
    when(notes.notes("shop", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(List.of(RequestNote.defaults().withAuthor(author).withKind(kind))),
                TokenUsage.NONE));

    List<String> lines =
        authors.lines(
            "ABC-42",
            RoundSource.REQUEST,
            "http://mr/1",
            List.of("http://mr/1#note_1\n" + author + ": fix it"));

    assertThat(lines).containsExactly(expected);
  }

  @Test
  void readsAnAuthorTheHostDoesNotNameAsPerson() {
    when(state.task("ABC-42"))
        .thenReturn(Optional.of(TaskState.builder("shop", "/wt", TaskStatus.CI_POLLING).build()));
    when(notes.notes("shop", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of(List.of()), TokenUsage.NONE));

    List<String> lines =
        authors.lines(
            "ABC-42", RoundSource.REQUEST, "http://mr/1", List.of("dana (a.kt:3): fix it"));

    assertThat(lines).containsExactly("Author: person dana");
  }

  @Test
  void givesKindUnknownWhereTheHostsNotesCouldNotBeRead() {
    when(state.task("ABC-42"))
        .thenReturn(Optional.of(TaskState.builder("shop", "/wt", TaskStatus.CI_POLLING).build()));
    when(notes.notes("shop", "http://mr/1"))
        .thenReturn(Answer.failed(TokenUsage.NONE, "rate limit exceeded"));

    List<String> lines =
        authors.lines(
            "ABC-42", RoundSource.REQUEST, "http://mr/1", List.of("dana (a.kt:3): fix it"));

    assertThat(lines).containsExactly("Author: unknown dana");
  }

  @Test
  void givesKindUnknownWhereTheTaskIsGone() {
    when(state.task("ABC-42")).thenReturn(Optional.empty());

    List<String> lines =
        authors.lines("ABC-42", RoundSource.REQUEST, "http://mr/1", List.of("dana: fix it"));

    assertThat(lines).containsExactly("Author: unknown dana");
  }

  @Test
  void readsNothingFromTheHostForThreadsOfTheTreeRead() {
    List<String> lines =
        authors.lines("ABC-42", RoundSource.TREE, "a tree", List.of("reviewer: leak"));

    assertThat(lines).containsExactly("Author: reviewer");
    verify(notes, never()).notes(any(), any());
  }

  @Test
  void findsTheKindOfAnAuthorTheHostNamesWithAnAtSignAndCapitals() {
    when(state.task("ABC-42"))
        .thenReturn(
            Optional.of(
                TaskState.builder("shop", "/wt", TaskStatus.CI_POLLING)
                    .repos(
                        List.of(
                            new TaskRepo(null, "/a", null, null),
                            new TaskRepo("shop", "/wt", null, null)))
                    .build()));
    when(notes.notes("shop", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    List.of(
                        RequestNote.defaults().withAuthor("bob").withKind(AuthorKind.PERSON),
                        RequestNote.defaults().withAuthor("@Review-Bot").withKind(AuthorKind.BOT))),
                TokenUsage.NONE));

    List<String> lines =
        authors.lines(
            "ABC-42",
            RoundSource.REQUEST,
            "http://mr/1",
            List.of("http://mr/1#note_1\nreview-bot: typo"));

    assertThat(lines).containsExactly("Author: bot review-bot");
  }
}

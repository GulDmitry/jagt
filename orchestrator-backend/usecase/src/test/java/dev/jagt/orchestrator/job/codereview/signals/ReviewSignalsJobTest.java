package dev.jagt.orchestrator.job.codereview.signals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.job.codereview.ReviewSubjects;
import dev.jagt.orchestrator.job.codereview.request.LedgerRow;
import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReviewSignalsJobTest {

  private final ReviewLedger ledger = mock(ReviewLedger.class);
  private final ReviewSubjects subjects = mock(ReviewSubjects.class);
  private final RequestNotes notes = mock(RequestNotes.class);
  private final SignalRecorder recorder = mock(SignalRecorder.class);
  private final ReviewSignalsJob job = new ReviewSignalsJob(ledger, subjects, notes, recorder);
  private final ReviewSubjects.Listing nothingListed =
      new ReviewSubjects.Listing(List.of(), Set.of());
  private final Answer<List<RequestNote>> noNotes =
      new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
  private final LedgerRow row = LedgerRow.of("https://h/a/b/7", "shop", "");
  private final RequestNote ours =
      RequestNote.defaults()
          .withId("5")
          .withKind(AuthorKind.ACTOR)
          .withBody("AI <!-- jagt-review:k1 -->");

  private final Answer<RequestChange> open =
      new Answer<>(Optional.of(RequestChange.defaults()), TokenUsage.NONE);

  private final Answer<List<RequestNote>> onlyOurs =
      new Answer<>(Optional.of(List.of(ours)), TokenUsage.NONE);

  @BeforeEach
  void openRequests() {
    when(notes.change(anyString(), anyString())).thenReturn(open);
  }

  @Test
  void failedReactionReadWritesNoLine() {
    when(ledger.rows()).thenReturn(List.of(row));
    when(subjects.list()).thenReturn(nothingListed);
    when(notes.notes("shop", row.url())).thenReturn(onlyOurs);
    when(notes.reactions(anyString(), anyString(), anyList()))
        .thenReturn(Answer.failed(TokenUsage.NONE, "rate limit exceeded"));

    job.run();

    verify(recorder, never()).record(any(), any(), any(), any());
    verify(ledger, never()).update(any(), any(), any(), any());
  }

  @Test
  void requestNoLongerListedIsReadOnceMoreAndItsRowEnds() {
    when(ledger.rows()).thenReturn(List.of(row));
    when(subjects.list()).thenReturn(nothingListed);
    when(notes.notes("shop", row.url())).thenReturn(noNotes);

    job.run();

    verify(ledger).update(any(), any(), any(), any());
  }

  @Test
  void rowIsKeptWhenTheListingOfItsProjectFailed() {
    when(ledger.rows()).thenReturn(List.of(row));
    when(subjects.list()).thenReturn(new ReviewSubjects.Listing(List.of(), Set.of("shop")));
    when(notes.notes("shop", row.url())).thenReturn(noNotes);

    job.run();

    verify(ledger, never()).update(any(), any(), any(), any());
  }

  @ParameterizedTest
  @CsvSource({"MERGED,1", "CLOSED,1", "OPEN,0"})
  void requestStillListedEndsItsRowOnlyWhereTheHostSaysItIsOver(RequestState state, int ends) {
    when(ledger.rows()).thenReturn(List.of(row));
    when(subjects.list())
        .thenReturn(
            new ReviewSubjects.Listing(List.of(new Subject("shop", row.url(), "r", "")), Set.of()));
    when(notes.notes("shop", row.url())).thenReturn(noNotes);
    when(notes.change("shop", row.url()))
        .thenReturn(
            new Answer<>(Optional.of(RequestChange.defaults().withState(state)), TokenUsage.NONE));

    job.run();

    verify(ledger, times(ends)).update(any(), any(), any(), any());
  }

  @ParameterizedTest
  @CsvSource({
    "'AI review note: react with a thumbs up if it helps, a thumbs down if it does not.', 1",
    "'AI note: 👍 helps, 👎 does not; reply with what is wrong.', 1",
    "'AI <!-- jagt-review:k1 -->', 1",
    "'looks good to me', 0"
  })
  void ratesOnlyWhatJagtPostedOnListedRequestNoRowKnowsYet(String body, int rated) {
    Subject subject = new Subject("shop", "https://h/a/b/9", "r", "t1");
    when(ledger.rows()).thenReturn(List.of());
    when(subjects.list()).thenReturn(new ReviewSubjects.Listing(List.of(subject), Set.of()));
    when(notes.notes("shop", subject.url()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    List.of(
                        RequestNote.defaults()
                            .withId("8")
                            .withKind(AuthorKind.ACTOR)
                            .withBody(body))),
                TokenUsage.NONE));
    when(notes.reactions(anyString(), anyString(), anyList()))
        .thenReturn(new Answer<>(Optional.of(Map.of()), TokenUsage.NONE));

    job.run();

    verify(notes, times(rated)).reactions("shop", subject.url(), List.of("8"));
  }

  @Test
  void humanReplyUnderOurNoteIsReadWithItsReactionsAndHandedToTheRecorder() {
    RequestNote threaded = ours.withThread("th1");
    RequestNote reply =
        RequestNote.defaults().withId("6").withThread("th1").withBody("the lock is held");
    RequestNote stranger =
        RequestNote.defaults().withId("7").withThread("th9").withBody("unrelated");
    when(ledger.rows()).thenReturn(List.of(row));
    when(subjects.list()).thenReturn(nothingListed);
    when(notes.notes("shop", row.url()))
        .thenReturn(new Answer<>(Optional.of(List.of(threaded, reply, stranger)), TokenUsage.NONE));
    Answer<Map<String, Reaction>> none = new Answer<>(Optional.of(Map.of()), TokenUsage.NONE);
    when(notes.reactions(anyString(), anyString(), anyList())).thenReturn(none);

    job.run();

    verify(notes).reactions("shop", row.url(), List.of("5", "6"));
    verify(recorder).record(row, Map.of("5", threaded), Map.of("6", reply), Map.of());
  }

  @ParameterizedTest
  @CsvSource({"'', 0", "t1, 1"})
  void readsAndKeepsRowOnlyForRequestsHeldByTasks(String taskId, int reads) {
    Subject subject = new Subject("shop", "https://h/a/b/9", "r", taskId);
    when(ledger.rows()).thenReturn(List.of());
    when(subjects.list()).thenReturn(new ReviewSubjects.Listing(List.of(subject), Set.of()));
    when(notes.notes("shop", subject.url())).thenReturn(noNotes);

    job.run();

    verify(notes, times(reads)).notes("shop", subject.url());
    verify(ledger, times(reads)).update(eq(subject.url()), eq("shop"), eq(taskId), any());
  }

  @Test
  void readsRequestOnceWhereItIsListedAndHoldsRow() {
    LedgerRow held = LedgerRow.of("https://h/a/b/9", "shop", "t1");
    Subject subject = new Subject("shop", held.url(), "r", "t1");
    when(ledger.rows()).thenReturn(List.of(held));
    when(subjects.list()).thenReturn(new ReviewSubjects.Listing(List.of(subject), Set.of()));
    when(notes.notes("shop", held.url())).thenReturn(noNotes);

    job.run();

    verify(notes, times(1)).notes("shop", held.url());
  }
}

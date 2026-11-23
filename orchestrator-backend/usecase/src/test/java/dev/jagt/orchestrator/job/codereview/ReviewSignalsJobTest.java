package dev.jagt.orchestrator.job.codereview;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.service.codereview.CodeReviewPanel;
import dev.jagt.orchestrator.service.codereview.LedgerRow;
import dev.jagt.orchestrator.service.codereview.ReviewLedger;
import dev.jagt.orchestrator.service.codereview.ReviewSubjects;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReviewSignalsJobTest {

  private final ReviewLedger ledger = mock(ReviewLedger.class);
  private final ReviewSubjects subjects = mock(ReviewSubjects.class);
  private final RequestNotes notes = mock(RequestNotes.class);
  private final ReviewStats stats = mock(ReviewStats.class);
  private final CodeReviewPanel panel = mock(CodeReviewPanel.class);
  private final ReviewSignalsJob job = new ReviewSignalsJob(ledger, subjects, notes, stats, panel);
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

  private final Answer<List<RequestNote>> onlyOurs =
      new Answer<>(Optional.of(List.of(ours)), TokenUsage.NONE);

  @Test
  void failedReactionReadWritesNoLine() {
    when(ledger.rows()).thenReturn(List.of(row));
    when(subjects.list()).thenReturn(nothingListed);
    when(notes.notes("shop", row.url())).thenReturn(onlyOurs);
    when(notes.reactions(anyString(), anyString(), anyList()))
        .thenReturn(Answer.failed(TokenUsage.NONE, "rate limit exceeded"));

    job.run();

    verify(stats, never())
        .append(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
    verify(ledger, never()).update(any(), any(), any(), any());
  }

  @Test
  void unchangedCountsWriteNoLine() {
    LedgerRow counted = row.withCounts(Map.of("5", "2/1/unknown"));
    when(ledger.rows()).thenReturn(List.of(counted));
    when(subjects.list())
        .thenReturn(
            new ReviewSubjects.Listing(
                List.of(
                    new dev.jagt.orchestrator.service.codereview.Subject(
                        "shop", row.url(), "r", "")),
                Set.of()));
    when(notes.notes("shop", row.url())).thenReturn(onlyOurs);
    when(notes.reactions(anyString(), anyString(), anyList()))
        .thenReturn(new Answer<>(Optional.of(Map.of("5", new Reaction(2, 1))), TokenUsage.NONE));
    when(panel.brief()).thenReturn("brief");

    job.run();

    verify(stats, never())
        .append(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
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
}

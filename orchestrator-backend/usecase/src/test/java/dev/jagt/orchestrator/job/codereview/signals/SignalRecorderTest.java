package dev.jagt.orchestrator.job.codereview.signals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.job.codereview.CodeReviewPanel;
import dev.jagt.orchestrator.job.codereview.request.LedgerRow;
import dev.jagt.orchestrator.job.codereview.request.NotePoster;
import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.job.codereview.request.ReviewResolutions;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignalRecorderTest {

  private final ReviewLedger ledger = mock(ReviewLedger.class);
  private final ReviewStats stats = mock(ReviewStats.class);
  private final CodeReviewPanel panel = mock(CodeReviewPanel.class);
  private final ReviewResolutions resolutions = mock(ReviewResolutions.class);
  private final SignalRecorder recorder = new SignalRecorder(ledger, stats, panel, resolutions);
  private final LedgerRow row = LedgerRow.of("https://h/a/b/pull/7", "shop", "t1");
  private final RequestNote ours =
      RequestNote.defaults()
          .withId("21")
          .withKind(AuthorKind.ACTOR)
          .withBody("AI <!-- jagt-review:k1 -->");

  @Test
  void noteTheSessionAnsweredIsResolvedAsThatAnswer() {
    when(panel.brief()).thenReturn("brief");
    when(resolutions.answered(row)).thenReturn(Map.of("21", "no-change"));

    recorder.record(row, Map.of("21", ours), Map.of(), Map.of("21", new Reaction(1, 2)));

    verify(stats)
        .append(
            row.url(),
            "21",
            "note",
            new ReviewStats.Rating(1, 2, "no-change", "unknown"),
            NotePoster.digest("brief"));
  }

  @Test
  void noteNoBlockAnsweredIsFixedWhereTheHostResolvedItsThread() {
    when(panel.brief()).thenReturn("brief");
    when(resolutions.answered(row)).thenReturn(Map.of());

    recorder.record(
        row,
        Map.of("21", ours.withResolution(Resolution.RESOLVED)),
        Map.of(),
        Map.of("21", new Reaction(0, 0)));

    verify(stats)
        .append(
            row.url(),
            "21",
            "note",
            new ReviewStats.Rating(0, 0, "fixed", ""),
            NotePoster.digest("brief"));
  }

  @Test
  void unchangedCountsWriteNoLine() {
    when(panel.brief()).thenReturn("brief");
    when(resolutions.answered(row)).thenReturn(Map.of());

    recorder.record(
        row.withCounts(Map.of("21", "2/1/unknown")),
        Map.of("21", ours),
        Map.of(),
        Map.of("21", new Reaction(2, 1)));

    verify(stats, never()).append(anyString(), anyString(), anyString(), any(), anyString());
  }

  @Test
  void unchangedCountsWithNoReasonWriteNoLine() {
    when(panel.brief()).thenReturn("brief");
    when(resolutions.answered(row)).thenReturn(Map.of());

    recorder.record(
        row.withCounts(Map.of("21", "1/0/unknown")),
        Map.of("21", ours),
        Map.of(),
        Map.of("21", new Reaction(1, 0)));

    verify(stats, never()).append(anyString(), anyString(), anyString(), any(), anyString());
  }

  @Test
  void replyLineCarriesNoBriefDigestBecauseTheReviewersBriefDidNotWriteIt() {
    when(panel.brief()).thenReturn("brief");
    when(resolutions.answered(row)).thenReturn(Map.of());

    recorder.record(
        row,
        Map.of("21", ours.withBody("AI review note: rate it")),
        Map.of(),
        Map.of("21", new Reaction(1, 0)));

    verify(stats).append(row.url(), "21", "reply", new ReviewStats.Rating(1, 0, "unknown", ""), "");
  }

  @Test
  void noteLineCarriesTheDigestOfTheBriefItWasWrittenUnder() {
    when(panel.brief()).thenReturn("brief");
    when(resolutions.answered(row)).thenReturn(Map.of());

    recorder.record(
        row,
        Map.of("21", ours.withBody("AI review note: rate it <!-- jagt-review:k1 -->")),
        Map.of(),
        Map.of("21", new Reaction(1, 0)));

    verify(stats)
        .append(
            row.url(),
            "21",
            "note",
            new ReviewStats.Rating(1, 0, "unknown", ""),
            NotePoster.digest("brief"));
  }

  @Test
  void humanReplyUnderNoteIsRecordedBesideItsRatingWithItsReactionsAsTheReason() {
    when(panel.brief()).thenReturn("brief");
    when(resolutions.answered(row)).thenReturn(Map.of());
    RequestNote reply =
        RequestNote.defaults()
            .withId("22")
            .withThread("th1")
            .withKind(AuthorKind.PERSON)
            .withBody("  the lock\nis held   already ");

    recorder.record(
        row,
        Map.of("21", ours.withThread("th1")),
        Map.of("22", reply),
        Map.of("21", new Reaction(0, 1), "22", new Reaction(2, 0)));

    verify(stats)
        .append(
            row.url(),
            "21",
            "note",
            new ReviewStats.Rating(0, 1, "unknown", "the lock is held already (+2/-0)"),
            NotePoster.digest("brief"));
  }

  @Test
  void thumbsDownWithNoReplyIsRecordedAsReasonUnknown() {
    when(panel.brief()).thenReturn("brief");
    when(resolutions.answered(row)).thenReturn(Map.of());

    recorder.record(
        row, Map.of("21", ours.withThread("th1")), Map.of(), Map.of("21", new Reaction(0, 1)));

    verify(stats)
        .append(
            row.url(),
            "21",
            "note",
            new ReviewStats.Rating(0, 1, "unknown", "unknown"),
            NotePoster.digest("brief"));
  }

  @Test
  void replyUnderAnotherThreadIsNotTheReason() {
    when(panel.brief()).thenReturn("brief");
    when(resolutions.answered(row)).thenReturn(Map.of());
    RequestNote elsewhere =
        RequestNote.defaults().withId("22").withThread("th2").withBody("unrelated");

    recorder.record(
        row,
        Map.of("21", ours.withThread("th1")),
        Map.of("22", elsewhere),
        Map.of("21", new Reaction(1, 0)));

    verify(stats)
        .append(
            row.url(),
            "21",
            "note",
            new ReviewStats.Rating(1, 0, "unknown", ""),
            NotePoster.digest("brief"));
  }
}

package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoundOutcomeTest {

  private final RoundRecord roundRecord = mock(RoundRecord.class);
  private final AgentStatusReports statusReports = mock(AgentStatusReports.class);
  private final RoundRelay relay = mock(RoundRelay.class);
  private final RoundOutcome outcome = new RoundOutcome(roundRecord, statusReports, relay);

  @BeforeEach
  void theRoundRecordsNothingUnusual() {
    when(roundRecord.record(any(), any(), any(), any()))
        .thenReturn(new RoundRecord.Recorded(Optional.empty(), RedCauses.Verdict.CODE));
  }

  @Test
  void recordsTheRoundItJudges() {
    ReviewFacts facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("running", "", ""),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));

    outcome.settle("ABC-1", "shop", "http://mr/1", facts, "");

    verify(roundRecord).record("ABC-1", "shop", facts, "");
  }

  @Test
  void saysTheTaskStoppedWhenItsChecksCouldNotBeListedOnceTooOften() {
    ReviewFacts facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("unknown", "list_pipelines: 403 Forbidden", ""),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));
    when(roundRecord.record("ABC-1", "shop", facts, ""))
        .thenReturn(
            new RoundRecord.Recorded(
                Optional.of(
                    new TaskStop(
                        TaskStop.Trigger.CHECKS,
                        3_000,
                        "3 reads running could not list the checks: list_pipelines: 403"
                            + " Forbidden")),
                RedCauses.Verdict.CODE));

    var result = outcome.settle("ABC-1", "shop", "http://mr/1", facts, "");

    assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.STOPPED);
  }

  @Test
  void advancesToApprovedOnceAHumanApprovedAndNoThreadIsLeftOpen() {
    var result =
        outcome.settle(
            "ABC-1",
            "shop",
            "http://mr/1",
            new ReviewFacts(
                new ReviewFacts.Request(true, true, false, false, 0),
                new ReviewFacts.Pipeline("success", "", ""),
                new ReviewFacts.Discussion(List.of(), List.of(), List.of())),
            "");

    assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.APPROVED);
    verify(statusReports).markRead("ABC-1", TaskStatus.APPROVED, Optional.empty());
    verify(statusReports, never()).markRead(eq("ABC-1"), eq(TaskStatus.REVIEWED), any());
  }

  @Test
  void marksAGreenRoundReviewedWhileNobodyHasApprovedItYet() {
    var result =
        outcome.settle(
            "ABC-1",
            "shop",
            "http://mr/1",
            new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 0),
                new ReviewFacts.Pipeline("success", "", ""),
                new ReviewFacts.Discussion(List.of(), List.of(), List.of())),
            "");

    assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.REVIEWED);
    verify(statusReports).markRead("ABC-1", TaskStatus.REVIEWED, Optional.empty());
    verify(statusReports, never()).markRead(eq("ABC-1"), eq(TaskStatus.APPROVED), any());
  }

  @Test
  void leavesAGreenThisSweepCouldNotReReadOutOfTheReviewedDecision() {
    var result =
        outcome.settle(
            "ABC-1",
            "shop",
            "http://mr/1",
            new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 0),
                new ReviewFacts.Pipeline("unknown", "", ""),
                new ReviewFacts.Discussion(List.of(), List.of(), List.of())),
            "");

    assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
    verify(statusReports, never()).markRead(eq("ABC-1"), eq(TaskStatus.REVIEWED), any());
  }

  @Test
  void holdsATaskBackWhileItsChecksAreStillRunning() {
    var result =
        outcome.settle(
            "ABC-1",
            "shop",
            "http://mr/1",
            new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 0),
                new ReviewFacts.Pipeline("running", "", ""),
                new ReviewFacts.Discussion(List.of(), List.of(), List.of())),
            "");

    assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
    assertThat(result.message()).contains("checks running");
    verify(statusReports, never()).markRead(eq("ABC-1"), eq(TaskStatus.REVIEWED), any());
  }

  @Test
  void stopsTheTaskOnTheBoardWhenItsChecksReadRed() {
    outcome.settle(
        "ABC-1",
        "shop",
        "http://mr/1",
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("failed", "", ""),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of())),
        "");

    verify(statusReports).markRead("ABC-1", TaskStatus.CI_FAILED, Optional.empty());
  }

  @Test
  void neverAdvancesARoundWithThreadsLeftEvenWhenApproved() {
    ReviewFacts facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, true, false, false, 0),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of("coderabbit (a.java:3): rename x"), List.of(), List.of()));
    when(relay.relay("ABC-1", "http://mr/1", facts, "success", ""))
        .thenReturn(
            new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.RELAYED, "relayed"));

    var result = outcome.settle("ABC-1", "shop", "http://mr/1", facts, "");

    assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.RELAYED);
    verify(statusReports, never()).markRead(eq("ABC-1"), eq(TaskStatus.APPROVED), any());
    verify(statusReports, never()).markRead(eq("ABC-1"), eq(TaskStatus.REVIEWED), any());
  }

  @Test
  void waitsOnTheReviewerWhileTheAgentsDeclineIsTheNewestNoteReadTwice() {
    ReviewFacts facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of(),
                List.of(
                    "http://mr/1#note_1\n"
                        + "mia: Should this cache expire?\n"
                        + "agent: No change: entries are immutable"),
                List.of()));
    outcome.settle("ABC-1", "shop", "http://mr/1", facts, "");

    var result = outcome.settle("ABC-1", "shop", "http://mr/1", facts, "");

    assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
    assertThat(result.message()).contains("waiting on the reviewer");
    verifyNoInteractions(relay);
    verify(statusReports, never()).markRead(eq("ABC-1"), eq(TaskStatus.REVIEWED), any());
  }

  @Test
  void aPipelineWhoseRunnerWasKilledIsRetriedRatherThanHandedToTheSession() {
    ReviewFacts facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("failed", "unit-tests", "https://host.invalid/p/1"),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));
    when(roundRecord.record("ABC-1", "shop", facts, ""))
        .thenReturn(
            new RoundRecord.Recorded(
                Optional.empty(),
                new RedCauses.Verdict(
                    RedCause.INFRASTRUCTURE,
                    true,
                    "runner_system_failure: unit-tests · retry 1/3 at 10:05 UTC",
                    Optional.empty())));

    var result = outcome.settle("ABC-1", "shop", "http://mr/1", facts, "");

    assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
    assertThat(result.message())
        .contains("retry armed (runner_system_failure: unit-tests · retry 1/3 at 10:05 UTC)");
    verify(statusReports, never()).markRead(eq("ABC-1"), eq(TaskStatus.CI_FAILED), any());
    verifyNoInteractions(relay);
  }

  @Test
  void aPipelineFailedOnInfrastructureStaysOutForReviewWithoutAQuestion() {
    ReviewFacts facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("failed", "unit-tests", "https://host.invalid/p/1"),
            new ReviewFacts.Discussion(List.of("mia: rename x"), List.of(), List.of("n1")));
    ReviewFacts threadsOnly = facts.withoutChecks();
    when(roundRecord.record("ABC-1", "shop", facts, ""))
        .thenReturn(
            new RoundRecord.Recorded(
                Optional.empty(),
                new RedCauses.Verdict(
                    RedCause.INFRASTRUCTURE,
                    true,
                    "runner_system_failure: unit-tests · retry 1/3 at 10:05 UTC",
                    Optional.empty())));

    outcome.settle("ABC-1", "shop", "http://mr/1", facts, "");

    verify(relay).relay("ABC-1", "http://mr/1", threadsOnly, "failed", "");
    verify(statusReports, never()).markRead(eq("ABC-1"), eq(TaskStatus.CI_FAILED), any());
  }

  @Test
  void landsCiFailedAndStopsTheTaskOnceTheRetriesRanOut() {
    ReviewFacts facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("failed", "unit-tests", "https://host.invalid/p/1"),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));
    TaskStop stop =
        new TaskStop(TaskStop.Trigger.INFRA, 3_000, "unit-tests: runner_system_failure, 3 retries");
    when(roundRecord.record("ABC-1", "shop", facts, ""))
        .thenReturn(
            new RoundRecord.Recorded(
                Optional.empty(),
                new RedCauses.Verdict(RedCause.CODE, false, "", Optional.of(stop))));

    var result = outcome.settle("ABC-1", "shop", "http://mr/1", facts, "");

    assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.STOPPED);
    verify(statusReports).markRead("ABC-1", TaskStatus.CI_FAILED, Optional.of(stop));
    verifyNoInteractions(relay);
  }
}

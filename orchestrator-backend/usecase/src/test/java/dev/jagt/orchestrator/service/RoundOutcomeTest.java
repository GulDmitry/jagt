package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.LoopWatch;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoundOutcomeTest {

    private final RoundRecord roundRecord = mock(RoundRecord.class);
    private final AgentStatusReports statusReports = mock(AgentStatusReports.class);
    private final RoundRelay relay = mock(RoundRelay.class);
    private final RoundOutcome outcome = new RoundOutcome(roundRecord, statusReports, relay);

    @Test
    void recordsTheRoundItJudges() {
        ReviewFacts facts = new ReviewFacts(true, false, "running", "", List.of(), 0, List.of());

        outcome.settle("ABC-1", "http://mr/1", facts);

        verify(roundRecord).record("ABC-1", facts);
    }

    @Test
    void saysTheTaskStoppedWhenItsChecksCouldNotBeListedOnceTooOften() {
        ReviewFacts facts = new ReviewFacts(true, false, "unknown", "list_pipelines: 403 Forbidden", List.of(), 0, List.of());
        when(roundRecord.record("ABC-1", facts)).thenReturn(Optional.of(new LoopWatch.Stop(LoopWatch.Trigger.CHECKS,
                3_000, "3 reads running could not list the checks: list_pipelines: 403 Forbidden")));

        var result = outcome.settle("ABC-1", "http://mr/1", facts);

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.STOPPED);
    }

    @Test
    void advancesToApprovedOnceAHumanApprovedAndNoThreadIsLeftOpen() {
        var result = outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, true, "success", "", List.of(), 0, List.of()));

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.APPROVED);
        verify(statusReports).markRead("ABC-1", TaskStatus.APPROVED);
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.REVIEWED);
    }

    @Test
    void marksAGreenRoundReviewedWhileNobodyHasApprovedItYet() {
        var result = outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, false, "success", "", List.of(), 0, List.of()));

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.REVIEWED);
        verify(statusReports).markRead("ABC-1", TaskStatus.REVIEWED);
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.APPROVED);
    }

    @Test
    void leavesAGreenThisSweepCouldNotReReadOutOfTheReviewedDecision() {
        var result = outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, false, "unknown", "", List.of(), 0, List.of()));

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.REVIEWED);
    }

    @Test
    void holdsATaskBackWhileItsChecksAreStillRunning() {
        var result = outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, false, "running", "", List.of(), 0, List.of()));

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
        assertThat(result.message()).contains("checks running");
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.REVIEWED);
    }

    @Test
    void stopsTheTaskOnTheBoardWhenItsChecksReadRed() {
        outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, false, "failed", "", List.of(), 0, List.of()));

        verify(statusReports).markRead("ABC-1", TaskStatus.CI_FAILED);
    }

    @Test
    void neverAdvancesARoundWithThreadsLeftEvenWhenApproved() {
        ReviewFacts facts = new ReviewFacts(true, true, "success", "", List.of("coderabbit (a.java:3): rename x"), 0, List.of());
        when(relay.relay("ABC-1", "http://mr/1", facts, "success")).thenReturn(
                new ReviewSweepService.SweepResult(ReviewSweepService.SweepResult.Kind.RELAYED, "relayed"));

        var result = outcome.settle("ABC-1", "http://mr/1", facts);

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.RELAYED);
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.APPROVED);
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.REVIEWED);
    }

    @Test
    void waitsOnTheReviewerWhileTheAgentsDeclineIsTheNewestNoteReadTwice() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", "", List.of(), 0, List.of(
                "http://mr/1#note_1\nmia: Should this cache expire?\nagent: No change: entries are immutable"));
        outcome.settle("ABC-1", "http://mr/1", facts);

        var result = outcome.settle("ABC-1", "http://mr/1", facts);

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
        assertThat(result.message()).contains("waiting on the reviewer");
        verifyNoInteractions(relay);
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.REVIEWED);
    }
}

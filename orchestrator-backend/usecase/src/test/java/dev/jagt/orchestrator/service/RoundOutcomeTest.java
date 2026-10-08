package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoundOutcomeTest {

    private final RoundRecord roundRecord = mock(RoundRecord.class);
    private final AgentStatusReports statusReports = mock(AgentStatusReports.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final RoundOutcome outcome = new RoundOutcome(roundRecord, statusReports, sessions);

    @Test
    void recordsTheRoundItJudges() {
        ReviewFacts facts = new ReviewFacts(true, false, "running", List.of());

        outcome.settle("ABC-1", "http://mr/1", facts);

        verify(roundRecord).record("ABC-1", facts);
    }

    @Test
    void advancesToApprovedOnceAHumanApprovedAndNoThreadIsLeftOpen() {
        var result = outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, true, "success", List.of()));

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.APPROVED);
        verify(statusReports).markRead("ABC-1", TaskStatus.APPROVED);
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.REVIEWED);
    }

    @Test
    void marksAGreenRoundReviewedWhileNobodyHasApprovedItYet() {
        var result = outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, false, "success", List.of()));

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.REVIEWED);
        verify(statusReports).markRead("ABC-1", TaskStatus.REVIEWED);
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.APPROVED);
    }

    @Test
    void leavesAGreenThisSweepCouldNotReReadOutOfTheReviewedDecision() {
        var result = outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, false, "unknown", List.of()));

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.REVIEWED);
    }

    @Test
    void holdsATaskBackWhileItsChecksAreStillRunning() {
        var result = outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, false, "running", List.of()));

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
        assertThat(result.message()).contains("checks running");
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.REVIEWED);
    }

    @Test
    void stopsTheTaskOnTheBoardWhenItsChecksReadRed() {
        outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, false, "failed", List.of()));

        verify(statusReports).markRead("ABC-1", TaskStatus.CI_FAILED);
    }

    @Test
    void relaysCommentsAsDraftsAndNeverAutoAdvancesEvenWhenApproved() {
        when(sessions.relayIfChanged(anyString(), anyString())).thenReturn(true);

        var result = outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, true, "success",
                List.of("coderabbit (a.java:3): rename x")));

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.RELAYED);
        verify(sessions).relayIfChanged(eq("ABC-1"), contains("review_replies.md"));
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.APPROVED);
        verify(statusReports, never()).markRead("ABC-1", TaskStatus.REVIEWED);
    }

    @Test
    void countsTheThreadsItRelayed() {
        when(sessions.relayIfChanged(anyString(), anyString())).thenReturn(true);

        var result = outcome.settle("ABC-1", "http://mr/api, http://mr/web", new ReviewFacts(true, false,
                "success", List.of("[api] http://mr/api#note_1", "[web] http://mr/web#note_2")));

        assertThat(result.message()).contains("2 thread(s) relayed");
    }

    @Test
    void reportsARoundUnchangedInsteadOfRelayingItASecondTime() {
        when(sessions.relayIfChanged(anyString(), anyString())).thenReturn(false);

        var result = outcome.settle("ABC-1", "http://mr/1", new ReviewFacts(true, false, "success",
                List.of("reviewer (a.java:3): drop the cache")));

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.UNCHANGED);
        assertThat(result.message()).contains("unchanged since the last relay");
    }
}

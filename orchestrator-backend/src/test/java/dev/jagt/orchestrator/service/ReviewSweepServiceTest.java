package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant;
import dev.jagt.orchestrator.assistant.MasterAssistant.ReviewFacts;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReviewSweepServiceTest {

    private final MasterAssistant assistant = mock(MasterAssistant.class);
    private final OrchestratorTools tools = mock(OrchestratorTools.class);
    private final ReviewSweepService sweep = new ReviewSweepService(assistant, tools);

    @Test
    void advancesToApprovedWhenTheMrIsApprovedAndClean() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(assistant.readReview("http://mr/1"))
                .thenReturn(Optional.of(new ReviewFacts(true, true, "success", List.of())));

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.APPROVED);
        verify(tools).markApproved("ABC-1");
        verify(tools, never()).markReviewed("ABC-1");
    }

    @Test
    void marksReviewedWhenGreenAndCleanButNotYetApproved() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(assistant.readReview("http://mr/1"))
                .thenReturn(Optional.of(new ReviewFacts(true, false, "success", List.of())));

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.REVIEWED);
        verify(tools).markReviewed("ABC-1");
        verify(tools, never()).markApproved("ABC-1");
    }

    @Test
    void relaysCommentsAsDraftsAndNeverAutoAdvancesEvenWhenApproved() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(assistant.readReview("http://mr/1")).thenReturn(Optional.of(
                new ReviewFacts(true, true, "success", List.of("coderabbit (a.java:3): rename x"))));

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.RELAYED);
        verify(tools).writeTaskContext(org.mockito.ArgumentMatchers.eq("ABC-1"),
                org.mockito.ArgumentMatchers.contains("review_replies.md"));
        verify(tools, never()).markApproved("ABC-1");
        verify(tools, never()).markReviewed("ABC-1");
    }

    @Test
    void reportsMissingMrWithoutReadingTheCodeHost() {
        when(tools.taskMrUrl("ABC-1")).thenReturn(null);

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.NO_MR);
        verifyNoInteractions(assistant);
    }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.model.ReviewFacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReviewSweepServiceTest {

    private final ReviewReader reviewReader = mock(ReviewReader.class);
    private final OrchestratorTools tools = mock(OrchestratorTools.class);
    private final StateService stateService = mock(StateService.class);
    private final ReviewSweepService sweep = new ReviewSweepService(reviewReader, tools, stateService);

    @BeforeEach
    void tasksAreAddressedByTheirIdUnlessATestSaysOtherwise() {
        when(stateService.canonicalTaskId(anyString())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void advancesToApprovedWhenTheMrIsApprovedAndClean() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1"))
                .thenReturn(Optional.of(new ReviewFacts(true, true, "success", List.of())));

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.APPROVED);
        verify(tools).markApproved("ABC-1");
        verify(tools, never()).markReviewed("ABC-1");
    }

    @Test
    void marksReviewedWhenGreenAndCleanButNotYetApproved() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1"))
                .thenReturn(Optional.of(new ReviewFacts(true, false, "success", List.of())));

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.REVIEWED);
        verify(tools).markReviewed("ABC-1");
        verify(tools, never()).markApproved("ABC-1");
    }

    @Test
    void relaysCommentsAsDraftsAndNeverAutoAdvancesEvenWhenApproved() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1")).thenReturn(Optional.of(new ReviewFacts(true, true,
                "success", List.of("coderabbit (a.java:3): rename x"))));

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.RELAYED);
        verify(tools).writeTaskContext(org.mockito.ArgumentMatchers.eq("ABC-1"),
                org.mockito.ArgumentMatchers.contains("review_replies.md"));
        verify(tools, never()).markApproved("ABC-1");
        verify(tools, never()).markReviewed("ABC-1");
    }

    /**
     * A relayed list of comments reads as a work order, and an agent handed a work order complies with the
     * wrong comments too — after which the human mistakes obedience in the diff for agreement. The brief has
     * to hand the agent a decision (fix / push back / ask) before it hands it the comments.
     */
    @Test
    void relaysAReviewRoundAsAJudgementCallAndNotAsAListOfOrders() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1")).thenReturn(Optional.of(new ReviewFacts(true, false,
                "success", List.of("reviewer (a.java:3): drop the cache"))));
        ArgumentCaptor<String> relayed = ArgumentCaptor.captor();

        sweep.sweep("ABC-1");

        verify(tools).writeTaskContext(org.mockito.ArgumentMatchers.eq("ABC-1"), relayed.capture());
        assertThat(relayed.getValue())
                .contains("Wrong: change NOTHING")          // disagreeing is a route, not a failure
                .contains("awaiting:")                      // so is asking, instead of guessing
                .contains("drop the cache");
    }

    /**
     * All three outcomes of a round end at REVIEW_PENDING, so the human is advised from the MESSAGE. Without
     * the "no changes" marker a round that touched nothing is advised as a ship, and that ship returns the task
     * to CI_POLLING where the next poll relays the same threads.
     */
    @Test
    void asksTheAgentToReportWhetherTheRoundChangedAnything() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1")).thenReturn(Optional.of(new ReviewFacts(true, false,
                "success", List.of("reviewer (a.java:3): drop the cache"))));
        ArgumentCaptor<String> relayed = ArgumentCaptor.captor();

        sweep.sweep("ABC-1");

        verify(tools).writeTaskContext(org.mockito.ArgumentMatchers.eq("ABC-1"), relayed.capture());
        assertThat(relayed.getValue())
                .contains("\"no changes: <why, few words>\"")
                .contains("Never say this if you edited a file")
                .contains("The file holds DRAFTS: post nothing and resolve");
    }

    /**
     * A red build with no comments goes through the same brief, and its exit condition has to be one the
     * agent can actually reach: it is forbidden to push, so it can never watch the pipeline turn green — only
     * finish the fix locally.
     */
    @Test
    void tellsAnAgentFixingOnlyAFailedBuildWhenTheRoundIsOver() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1"))
                .thenReturn(Optional.of(new ReviewFacts(true, false, "failed", List.of())));
        ArgumentCaptor<String> relayed = ArgumentCaptor.captor();

        sweep.sweep("ABC-1");

        verify(tools).writeTaskContext(org.mockito.ArgumentMatchers.eq("ABC-1"), relayed.capture());
        assertThat(relayed.getValue()).contains("When the build is fixed locally, set status REVIEW_PENDING.");
    }

    @Test
    void reportsAnUnreadableReviewInsteadOfTreatingItAsClean() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1")).thenReturn(Optional.empty());

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.UNREADABLE);
        verify(tools, never()).markReviewed("ABC-1");
        verify(tools, never()).markApproved("ABC-1");
    }

    @Test
    void refusesASecondSweepOfATaskWhileTheFirstIsStillRunning() {
        // The trigger does not matter — a human typing `review` during an auto-poll used to spawn a second
        // headless read of the same merge request: paid for twice, and two briefs relayed for one round.
        var reentrant = new AtomicReference<ReviewSweepService.SweepResult>();
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1")).thenAnswer(call -> {
            reentrant.set(sweep.sweep("ABC-1"));
            return Optional.of(new ReviewFacts(true, false, "running", List.of()));
        });

        sweep.sweep("ABC-1");

        assertThat(reentrant.get().kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.IN_FLIGHT);
        assertThat(reentrant.get().message()).contains("already running");
        verify(reviewReader, times(1)).read("ABC-1", "http://mr/1");
    }

    @Test
    void guardsAnAliasAndItsTaskIdAsOneAndTheSameSweep() {
        var reentrant = new AtomicReference<ReviewSweepService.SweepResult>();
        when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1")).thenAnswer(call -> {
            reentrant.set(sweep.sweep("a1"));
            return Optional.of(new ReviewFacts(true, false, "running", List.of()));
        });

        sweep.sweep("ABC-1");

        assertThat(reentrant.get().kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.IN_FLIGHT);
    }

    @Test
    void sweepsAgainOnceThePreviousSweepHasFinished() {
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1"))
                .thenReturn(Optional.of(new ReviewFacts(true, false, "running", List.of())));

        sweep.sweep("ABC-1");
        var second = sweep.sweep("ABC-1");

        assertThat(second.kind()).isNotEqualTo(ReviewSweepService.SweepResult.Kind.IN_FLIGHT);
        verify(reviewReader, times(2)).read("ABC-1", "http://mr/1");
    }

    @Test
    void refusesAConcurrentSweepFromAnotherThreadNotJustAReentrantCall() throws InterruptedException {
        // The real collision is the shell thread (`review`) against the auto-review executor thread, and the
        // reentrant tests above only ever exercise one thread. What this pins is cross-thread EXCLUSION: the
        // second caller is refused while the first is still inside. (It cannot prove the absence of a data
        // race — the latches introduce the very ordering a race needs to lack — so the concurrent set in
        // ReviewSweepService is a deliberate choice, not something a test can catch being reverted.)
        CountDownLatch firstSweepIsInside = new CountDownLatch(1);
        CountDownLatch secondSweepReturned = new CountDownLatch(1);
        AtomicReference<ReviewSweepService.SweepResult> fromOtherThread = new AtomicReference<>();
        when(tools.taskMrUrl("ABC-1")).thenReturn("http://mr/1");
        when(reviewReader.read("ABC-1", "http://mr/1")).thenAnswer(call -> {
            firstSweepIsInside.countDown();
            secondSweepReturned.await(5, TimeUnit.SECONDS);
            return Optional.of(new ReviewFacts(true, false, "running", List.of()));
        });
        Thread contender = new Thread(() -> {
            try {
                firstSweepIsInside.await(5, TimeUnit.SECONDS);
                fromOtherThread.set(sweep.sweep("ABC-1"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                secondSweepReturned.countDown();
            }
        }, "contending-sweep");

        contender.start();
        sweep.sweep("ABC-1");
        contender.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(fromOtherThread.get().kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.IN_FLIGHT);
        verify(reviewReader, times(1)).read("ABC-1", "http://mr/1");
    }

    @Test
    void reportsMissingMrWithoutReadingTheCodeHost() {
        when(tools.taskMrUrl("ABC-1")).thenReturn(null);

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.NO_MR);
        verifyNoInteractions(reviewReader);
    }
}

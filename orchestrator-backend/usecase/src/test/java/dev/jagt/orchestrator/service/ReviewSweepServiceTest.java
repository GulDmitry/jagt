package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ReviewFacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReviewSweepServiceTest {

    private final StateService stateService = mock(StateService.class);
    private final RoundReading reading = mock(RoundReading.class);
    private final RoundOutcome outcome = mock(RoundOutcome.class);
    private final ReviewSweepService sweep = new ReviewSweepService(stateService, reading, outcome);

    @BeforeEach
    void aTaskWhoseIdIsItsOwn() {
        when(stateService.canonicalTaskId(anyString())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void settlesTheRoundItRead() {
        ReviewFacts facts = new ReviewFacts(true, true, "success", List.of());
        when(reading.read("ABC-1")).thenReturn(new RoundReading.Round("http://mr/1", facts));
        when(outcome.settle("ABC-1", "http://mr/1", facts)).thenReturn(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.APPROVED, "approved"));

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.APPROVED);
    }

    @Test
    void judgesNothingWhereThereIsNoRoundToRead() {
        when(reading.read("ABC-1")).thenReturn(new RoundReading.Refused(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.UNREADABLE, "error: read failed: http://mr/1")));

        var result = sweep.sweep("ABC-1");

        assertThat(result.kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.UNREADABLE);
        verifyNoInteractions(outcome);
    }

    @Test
    void refusesASecondSweepOfATaskWhileTheFirstIsStillRunning() {
        var reentrant = new AtomicReference<ReviewSweepService.SweepResult>();
        when(reading.read("ABC-1")).thenAnswer(call -> {
            reentrant.set(sweep.sweep("ABC-1"));
            return new RoundReading.Refused(new ReviewSweepService.SweepResult(
                    ReviewSweepService.SweepResult.Kind.PENDING, "waiting"));
        });

        sweep.sweep("ABC-1");

        assertThat(reentrant.get().kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.IN_FLIGHT);
        assertThat(reentrant.get().message()).contains("already running");
        verify(reading, times(1)).read("ABC-1");
    }

    @Test
    void guardsAnAliasAndItsTaskIdAsOneAndTheSameSweep() {
        var reentrant = new AtomicReference<ReviewSweepService.SweepResult>();
        when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");
        when(reading.read("ABC-1")).thenAnswer(call -> {
            reentrant.set(sweep.sweep("a1"));
            return new RoundReading.Refused(new ReviewSweepService.SweepResult(
                    ReviewSweepService.SweepResult.Kind.PENDING, "waiting"));
        });

        sweep.sweep("ABC-1");

        assertThat(reentrant.get().kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.IN_FLIGHT);
    }

    @Test
    void sweepsAgainOnceThePreviousSweepHasFinished() {
        when(reading.read("ABC-1")).thenReturn(new RoundReading.Refused(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.PENDING, "waiting")));

        sweep.sweep("ABC-1");
        var second = sweep.sweep("ABC-1");

        assertThat(second.kind()).isNotEqualTo(ReviewSweepService.SweepResult.Kind.IN_FLIGHT);
        verify(reading, times(2)).read("ABC-1");
    }

    @Test
    void refusesAConcurrentSweepFromAnotherThreadNotJustAReentrantCall() throws InterruptedException {
        CountDownLatch firstSweepIsInside = new CountDownLatch(1);
        CountDownLatch secondSweepReturned = new CountDownLatch(1);
        AtomicReference<ReviewSweepService.SweepResult> fromOtherThread = new AtomicReference<>();
        when(reading.read("ABC-1")).thenAnswer(call -> {
            firstSweepIsInside.countDown();
            secondSweepReturned.await(5, TimeUnit.SECONDS);
            return new RoundReading.Refused(new ReviewSweepService.SweepResult(
                    ReviewSweepService.SweepResult.Kind.PENDING, "waiting"));
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
        verify(reading, times(1)).read(any());
    }
}

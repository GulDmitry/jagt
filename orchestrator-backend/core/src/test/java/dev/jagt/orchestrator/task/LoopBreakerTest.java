package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoopBreakerTest {

    private final LoopBreaker breaker = new LoopBreaker(3, 4, Duration.ofHours(24));

    @Test
    void stopsAfterThreeRoundsRunningThatResolvedNoThreadWithTheSameJobFailing() {
        LoopWatch watch = new LoopWatch(List.of(new LoopWatch.Round(1, 10, "unit-tests", "a"),
                new LoopWatch.Round(2, 10, "unit-tests", "b"), new LoopWatch.Round(3, 10, "unit-tests", "c"),
                new LoopWatch.Round(4, 11, "unit-tests", "d")), 0, null);

        var stop = breaker.afterRound(watch, List.of(), 5);

        assertThat(stop).hasValueSatisfying(it -> {
            assertThat(it.trigger()).isEqualTo(LoopWatch.Trigger.STALLED);
            assertThat(it.evidence()).contains("threads per round: 10, 10, 10, 11");
        });
    }

    @Test
    void keepsRelayingTheObservedRoundsWhereThreadsKeptBeingResolved() {
        LoopWatch watch = new LoopWatch(List.of(new LoopWatch.Round(1, 10, "sonar", "a"),
                new LoopWatch.Round(2, 10, "sonar", "b"), new LoopWatch.Round(3, 6, "", "c"),
                new LoopWatch.Round(4, 5, "", "d"), new LoopWatch.Round(5, 5, "", "e"),
                new LoopWatch.Round(6, 5, "", "f")), 0, null);

        assertThat(breaker.afterRound(watch, List.of(), 7)).isEmpty();
    }

    @Test
    void keepsRelayingWhenAnotherJobFailsThanTheRoundBefore() {
        LoopWatch watch = new LoopWatch(List.of(new LoopWatch.Round(1, 10, "unit-tests", "a"),
                new LoopWatch.Round(2, 10, "unit-tests", "b"), new LoopWatch.Round(3, 10, "unit-tests", "c"),
                new LoopWatch.Round(4, 10, "lint", "d")), 0, null);

        assertThat(breaker.afterRound(watch, List.of(), 5)).isEmpty();
    }

    @Test
    void stopsTheFifthShipWithinTheWindowEvenWhileThreadsAreResolved() {
        LoopWatch watch = new LoopWatch(List.of(new LoopWatch.Round(1, 10, "", "a"),
                new LoopWatch.Round(2, 6, "", "b"), new LoopWatch.Round(3, 5, "", "c")), 0, null);
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 4_000, ActionOrigin.MASTER));

        var stop = breaker.beforeShip(watch, history, 5_000);

        assertThat(stop).hasValueSatisfying(it -> {
            assertThat(it.trigger()).isEqualTo(LoopWatch.Trigger.SHIPS);
            assertThat(it.evidence()).contains("ships in 24h: 4");
        });
    }

    @Test
    void countsNoShipFromBeforeTheHumanLiftedTheLastStop() {
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 4_000, ActionOrigin.MASTER));

        assertThat(breaker.beforeShip(LoopWatch.liftedAt(4_500), history, 5_000)).isEmpty();
    }

    @Test
    void countsNoShipOlderThanTheWindow() {
        long day = Duration.ofHours(24).toMillis();
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, day + 4_000, ActionOrigin.MASTER));

        assertThat(breaker.beforeShip(LoopWatch.none(), history, day + 5_000)).isEmpty();
    }
}

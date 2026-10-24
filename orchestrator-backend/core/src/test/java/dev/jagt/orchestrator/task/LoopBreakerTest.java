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
    void stopsTheFifthShipWithinTheWindowWhereThreadsOscillateWithoutANewLow() {
        LoopWatch watch = new LoopWatch(List.of(new LoopWatch.Round(500, 5, "", "a"),
                new LoopWatch.Round(1_500, 6, "", "b"), new LoopWatch.Round(2_500, 5, "", "c"),
                new LoopWatch.Round(3_500, 6, "", "d"), new LoopWatch.Round(4_500, 5, "", "e")), 0, null);
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 4_000, ActionOrigin.MASTER));

        var stop = breaker.beforeShip(watch, history, 5_000);

        assertThat(stop).hasValueSatisfying(it -> {
            assertThat(it.trigger()).isEqualTo(LoopWatch.Trigger.SHIPS);
            assertThat(it.evidence()).contains("ships without a new low in 24h: 4")
                    .contains("threads per round: 5, 6, 5, 6, 5");
        });
    }

    @Test
    void letsATaskShipAgainWhileEveryRoundBroughtThreadsToANewLow() {
        LoopWatch watch = new LoopWatch(List.of(new LoopWatch.Round(500, 3, "", "a"),
                new LoopWatch.Round(1_500, 2, "", "b"), new LoopWatch.Round(2_500, 1, "", "c"),
                new LoopWatch.Round(3_500, 0, "", "d")), 0, null);
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 4_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 4_500, ActionOrigin.MASTER));

        assertThat(breaker.beforeShip(watch, history, 5_000)).isEmpty();
    }

    @Test
    void countsEveryShipWhereNoRoundHasThreadData() {
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 4_000, ActionOrigin.MASTER));

        var stop = breaker.beforeShip(LoopWatch.none(), history, 5_000);

        assertThat(stop).hasValueSatisfying(it -> assertThat(it.evidence())
                .contains("ships without a new low in 24h: 4"));
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

    @Test
    void sendsARoundAgainOnceTheHumanLiftedTheStopItsRunTripped() {
        Turn turn = Turn.none().relayed("verify", "a", 1).relayed("verify", "b", 2).relayed("verify", "c", 3);

        var stop = LoopBreaker.beforeRound("verify", turn, LoopWatch.liftedAt(3), 4);

        assertThat(stop).isEmpty();
    }

    @Test
    void forgetsRoundsOlderThanItsWindow() {
        Turn turn = Turn.none().relayed("verify", "a", 1).relayed("verify", "b", 2).relayed("verify", "c", 3);

        var stop = LoopBreaker.beforeRound("verify", turn, LoopWatch.none(), Duration.ofHours(25).toMillis());

        assertThat(stop).isEmpty();
    }

    @Test
    void countsOnlyTheRoundsOfTheStepRunningSinceAnotherStepWasRelayed() {
        Turn turn = Turn.none().relayed("verify", "a", 1).relayed("verify", "b", 2).relayed("answered", "c", 3)
                .relayed("verify", "d", 4);

        var stop = LoopBreaker.beforeRound("verify", turn, LoopWatch.none(), 5);

        assertThat(stop).isEmpty();
    }
}

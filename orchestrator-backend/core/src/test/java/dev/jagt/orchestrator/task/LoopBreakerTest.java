package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoopBreakerTest {

    private final LoopBreaker breaker = new LoopBreaker(3, 4, 3, 20, Duration.ofHours(24));

    @Test
    void stopsAfterThreeRoundsRunningThatBroughtNoNewNoteWithTheSameJobFailing() {
        RoundTrail watch = new RoundTrail(List.of(new RoundTrail.Round(1, 10, "unit-tests", "a", "t1 note_1"),
                new RoundTrail.Round(2, 10, "unit-tests", "b", "t1 note_1"),
                new RoundTrail.Round(3, 10, "unit-tests", "c", "t1 note_1"),
                new RoundTrail.Round(4, 11, "unit-tests", "d", "t1 note_1")), 0);

        var stop = breaker.afterRound(watch, List.of(), 5);

        assertThat(stop).hasValueSatisfying(it -> {
            assertThat(it.trigger()).isEqualTo(TaskStop.Trigger.STALLED);
            assertThat(it.evidence()).contains("threads per round: 10, 10, 10, 11");
        });
    }

    @Test
    void keepsRelayingWhereEveryRoundBroughtANewNote() {
        RoundTrail watch = new RoundTrail(List.of(new RoundTrail.Round(1, 10, "sonar", "a", "t1 note_1"),
                new RoundTrail.Round(2, 10, "sonar", "b", "t1 note_2"), new RoundTrail.Round(3, 6, "", "c", "t1 note_3"),
                new RoundTrail.Round(4, 5, "", "d", "t1 note_4"), new RoundTrail.Round(5, 5, "", "e", "t1 note_5"),
                new RoundTrail.Round(6, 5, "", "f", "t1 note_6")), 0);

        assertThat(breaker.afterRound(watch, List.of(), 7)).isEmpty();
    }

    @Test
    void countsAgainFromANewNoteWhereTheRoundsBeforeBroughtNone() {
        RoundTrail watch = new RoundTrail(List.of(new RoundTrail.Round(1, 2, "", "a", "t1 note_1"),
                new RoundTrail.Round(2, 2, "", "b", "t1 note_1"), new RoundTrail.Round(3, 2, "", "c", "t1 note_1"),
                new RoundTrail.Round(4, 2, "", "d", "t1 note_2"), new RoundTrail.Round(5, 2, "", "e", "t1 note_2")), 0);

        assertThat(breaker.afterRound(watch, List.of(), 6)).isEmpty();
    }

    @Test
    void keepsRelayingWhenAnotherJobFailsThanTheRoundBefore() {
        RoundTrail watch = new RoundTrail(List.of(new RoundTrail.Round(1, 10, "unit-tests", "a", ""),
                new RoundTrail.Round(2, 10, "unit-tests", "b", ""), new RoundTrail.Round(3, 10, "unit-tests", "c", ""),
                new RoundTrail.Round(4, 10, "lint", "d", "")), 0);

        assertThat(breaker.afterRound(watch, List.of(), 5)).isEmpty();
    }

    @Test
    void stopsTheFifthShipWithinTheWindowWhereNoRoundBroughtANewNote() {
        RoundTrail watch = new RoundTrail(List.of(new RoundTrail.Round(500, 5, "", "a", "t1 note_1"),
                new RoundTrail.Round(1_500, 6, "", "b", "t1 note_1"), new RoundTrail.Round(2_500, 5, "", "c", "t1 note_1"),
                new RoundTrail.Round(3_500, 6, "", "d", "t1 note_1"), new RoundTrail.Round(4_500, 5, "", "e", "t1 note_1")), 0);
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 4_000, ActionOrigin.MASTER));

        var stop = breaker.beforeShip(watch, history, 5_000);

        assertThat(stop).hasValueSatisfying(it -> {
            assertThat(it.trigger()).isEqualTo(TaskStop.Trigger.SHIPS);
            assertThat(it.evidence()).contains("ships without a new note in 24h: 4")
                    .contains("threads per round: 5, 6, 5, 6, 5");
        });
    }

    @Test
    void neverStopsAsStalledRoundsWhoseNoteFingerprintIsBlank() {
        RoundTrail watch = new RoundTrail(List.of(new RoundTrail.Round(1, 10, "unit-tests", "a", ""),
                new RoundTrail.Round(2, 10, "unit-tests", "b", ""), new RoundTrail.Round(3, 10, "unit-tests", "c", ""),
                new RoundTrail.Round(4, 10, "unit-tests", "d", "")), 0);

        assertThat(breaker.afterRound(watch, List.of(), 5)).isEmpty();
    }

    @Test
    void countsNoShipTowardTheCeilingWhereTheAnsweringRoundHasABlankNoteFingerprint() {
        RoundTrail watch = new RoundTrail(List.of(new RoundTrail.Round(500, 5, "", "a", ""),
                new RoundTrail.Round(1_500, 6, "", "b", ""), new RoundTrail.Round(2_500, 5, "", "c", ""),
                new RoundTrail.Round(3_500, 6, "", "d", ""), new RoundTrail.Round(4_500, 5, "", "e", "")), 0);
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 4_000, ActionOrigin.MASTER));

        assertThat(breaker.beforeShip(watch, history, 5_000)).isEmpty();
    }

    @Test
    void letsATaskShipAgainWhileEveryRoundBroughtANewNote() {
        RoundTrail watch = new RoundTrail(List.of(new RoundTrail.Round(500, 3, "", "a", "t1 note_1"),
                new RoundTrail.Round(1_500, 2, "", "b", "t1 note_2"), new RoundTrail.Round(2_500, 1, "", "c", "t1 note_3"),
                new RoundTrail.Round(3_500, 0, "", "d", "t1 note_4")), 0);
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

        var stop = breaker.beforeShip(RoundTrail.none(), history, 5_000);

        assertThat(stop).hasValueSatisfying(it -> assertThat(it.evidence())
                .contains("ships without a new note in 24h: 4"));
    }

    @Test
    void countsNoShipFromBeforeTheHumanLiftedTheLastStop() {
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 4_000, ActionOrigin.MASTER));

        assertThat(breaker.beforeShip(RoundTrail.liftedAt(4_500), history, 5_000)).isEmpty();
    }

    @Test
    void countsNoShipOlderThanTheWindow() {
        long day = Duration.ofHours(24).toMillis();
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, day + 4_000, ActionOrigin.MASTER));

        assertThat(breaker.beforeShip(RoundTrail.none(), history, day + 5_000)).isEmpty();
    }

    @Test
    void sendsARoundAgainOnceTheHumanLiftedTheStopItsRunTripped() {
        Turn turn = Turn.none().relayed("verify", "a", "", 1).relayed("verify", "b", "", 2).relayed("verify", "c", "", 3);

        var stop = breaker.beforeRound("verify", turn, RoundTrail.liftedAt(3), 4);

        assertThat(stop).isEmpty();
    }

    @Test
    void countsNoRoundSentAtTheMomentOfTheLift() {
        Turn turn = Turn.none().relayed("verify", "a", "", 1).relayed("verify", "b", "", 2).relayed("verify", "c", "", 3);

        var stop = breaker.beforeRound("verify", turn, RoundTrail.liftedAt(1), 4);

        assertThat(stop).isEmpty();
    }

    @Test
    void countsNoShipAskedAtTheMomentOfTheLift() {
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 2_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 3_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 4_000, ActionOrigin.MASTER));

        assertThat(breaker.beforeShip(RoundTrail.liftedAt(1_000), history, 5_000)).isEmpty();
    }

    @Test
    void countsAShipAsAnsweringTheRoundReadAtTheSameMoment() {
        RoundTrail watch = new RoundTrail(List.of(new RoundTrail.Round(100, 5, "", "a", "t1 note_1"),
                new RoundTrail.Round(1_000, 3, "", "b", "t1 note_2")), 0);
        List<StatusChange> history = List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, ActionOrigin.MASTER));

        var stop = new LoopBreaker(3, 1, 3, 20, Duration.ofHours(24)).beforeShip(watch, history, 2_000);

        assertThat(stop).isEmpty();
    }

    @Test
    void forgetsRoundsOlderThanItsWindow() {
        Turn turn = Turn.none().relayed("verify", "a", "", 1).relayed("verify", "b", "", 2).relayed("verify", "c", "", 3);

        var stop = breaker.beforeRound("verify", turn, RoundTrail.none(), Duration.ofHours(25).toMillis());

        assertThat(stop).isEmpty();
    }

    @Test
    void countsOnlyTheRoundsOfTheStepRunningSinceAnotherStepWasRelayed() {
        Turn turn = Turn.none().relayed("verify", "a", "", 1).relayed("verify", "b", "", 2).relayed("answered", "c", "", 3)
                .relayed("verify", "d", "", 4);

        var stop = breaker.beforeRound("verify", turn, RoundTrail.none(), 5);

        assertThat(stop).isEmpty();
    }

    @Test
    void stopsTheRoundPastTheCeilingTheOwnerSet() {
        Turn turn = Turn.none().relayed("round-not-ready", "a", "", 1).relayed("round-not-ready", "b", "", 2);

        var stop = new LoopBreaker(3, 4, 2, 20, Duration.ofHours(24)).beforeRound("round-not-ready", turn, RoundTrail.none(), 3);

        assertThat(stop).hasValueSatisfying(it -> assertThat(it.evidence())
                .isEqualTo("round-not-ready sent 2 rounds running in 24h; the next one was not"));
    }

    @Test
    void sendsARoundAgainWhereEachOfTheRoundsRunningAnsweredAnotherReport() {
        Turn turn = Turn.none().relayed("round-not-ready", "a", "plan 1", 1).relayed("round-not-ready", "b", "plan 2", 2)
                .relayed("round-not-ready", "c", "plan 3", 3);

        var stop = breaker.beforeRound("round-not-ready", turn, RoundTrail.none(), 4);

        assertThat(stop).isEmpty();
    }

    @Test
    void stopsTheRoundWhereEachOfTheRoundsRunningAnsweredTheSameReport() {
        Turn turn = Turn.none().relayed("round-not-ready", "a", "plan 1", 1).relayed("round-not-ready", "b", "plan 1", 2)
                .relayed("round-not-ready", "c", "plan 1", 3);

        var stop = breaker.beforeRound("round-not-ready", turn, RoundTrail.none(), 4);

        assertThat(stop).hasValueSatisfying(it -> assertThat(it.trigger()).isEqualTo(TaskStop.Trigger.ROUNDS));
    }

    @Test
    void stopsTheMoveWhereTheTaskMadeTwentyUnattendedMovesInTheHour() {
        List<StatusChange> history = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> new StatusChange(TaskStatus.CI_POLLING, 3_000_000 + i, ActionOrigin.MASTER)).toList();

        var stop = breaker.beforeMove(history, RoundTrail.none(), 3_100_000);

        assertThat(stop).hasValueSatisfying(it -> {
            assertThat(it.trigger()).isEqualTo(TaskStop.Trigger.PACE);
            assertThat(it.evidence()).isEqualTo("20 unattended moves in 1h; the next one was not made");
        });
    }

    @ParameterizedTest
    @EnumSource(names = {"MASTER", "AUTO_REVIEW", "TRACKER"})
    void countsTheMovesOfEveryUnattendedOrigin(ActionOrigin origin) {
        List<StatusChange> history = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> new StatusChange(TaskStatus.CI_POLLING, 3_000_000 + i, origin)).toList();

        var stop = breaker.beforeMove(history, RoundTrail.none(), 3_100_000);

        assertThat(stop).isPresent();
    }

    @ParameterizedTest
    @EnumSource(names = {"BOARD", "PALETTE", "MCP"})
    void countsNoMoveASessionOrTheHumanMade(ActionOrigin origin) {
        List<StatusChange> history = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> new StatusChange(TaskStatus.CI_POLLING, 3_000_000 + i, origin)).toList();

        var stop = breaker.beforeMove(history, RoundTrail.none(), 3_100_000);

        assertThat(stop).isEmpty();
    }

    @Test
    void allowsTheMoveWhereTheTwentyMovesStartedBeforeTheLastHour() {
        List<StatusChange> history = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> new StatusChange(TaskStatus.CI_POLLING, 3_000_000 + i, ActionOrigin.MASTER)).toList();

        var stop = breaker.beforeMove(history, RoundTrail.none(), 6_700_000);

        assertThat(stop).isEmpty();
    }

    @Test
    void allowsTheMoveWhereSomeOfTheTwentyCameBeforeTheLift() {
        List<StatusChange> history = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> new StatusChange(TaskStatus.CI_POLLING, 3_000_000 + i, ActionOrigin.MASTER)).toList();

        var stop = breaker.beforeMove(history, RoundTrail.liftedAt(3_000_010), 3_100_000);

        assertThat(stop).isEmpty();
    }
}

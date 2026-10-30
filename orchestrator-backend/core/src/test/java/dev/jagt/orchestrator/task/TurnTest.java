package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class TurnTest {

    @Test
    void opensWhenTheSessionHoldsAStepItWasRelayed() {
        Turn relayed = Turn.none().relayed("round", "d1", "", 100);

        assertThat(relayed.open()).isTrue();
        assertThat(relayed.step()).isEqualTo("round");
        assertThat(relayed.digest()).isEqualTo("d1");
        assertThat(relayed.since()).isEqualTo(100);
    }

    @Test
    void isNotOpenOnceTheSessionReportedAndEmptiedTheDigest() {
        Turn reported = Turn.none().relayed("round", "d1", "", 100).closed(200);

        assertThat(reported.open()).isFalse();
    }

    @Test
    void holdsOnlyTheSameStepWithTheSameFactsWhileOpen() {
        Turn open = Turn.none().relayed("round", "d1", "", 100);

        assertThat(open.holds("round", "d1")).isTrue();
        assertThat(open.holds("round", "d2")).isFalse();
        assertThat(open.holds("plan", "d1")).isFalse();
        assertThat(open.closed(200).holds("round", "d1")).isFalse();
    }

    @Test
    void countsTheNextTurnAtEveryRelayAndEveryReport() {
        Turn relayed = Turn.none().relayed("round", "d1", "", 100);
        Turn closed = relayed.closed(200);

        assertThat(relayed.id()).isEqualTo(1);
        assertThat(closed.id()).isEqualTo(2);
        assertThat(closed.digest()).isEmpty();
        assertThat(closed.since()).isEqualTo(200);
        assertThat(closed.step()).isEqualTo("round");
    }

    @Test
    void isTheSameTurnOnlyWhileIdAndSinceBothMatch() {
        Turn turn = new Turn("round", 4, "d1", 100, Map.of(), List.of());

        assertThat(turn.sameTurnAs(new Turn("plan", 4, "d9", 100, Map.of("a", "b"), List.of())))
                .isTrue();
        assertThat(turn.sameTurnAs(new Turn("round", 5, "d1", 100, Map.of(), List.of()))).isFalse();
        assertThat(turn.sameTurnAs(new Turn("round", 4, "d1", 101, Map.of(), List.of()))).isFalse();
    }

    @Test
    void remembersTheLastValueEachStepCarriedAndAnswersAnUnknownStepWithNull() {
        Turn relayed = Turn.none().relayed("round", "d1", "", 100).relayed("plan", "p1", "", 200);

        assertThat(relayed.mark("round")).isEqualTo("d1");
        assertThat(relayed.mark("plan")).isEqualTo("p1");
        assertThat(relayed.mark("ship")).isNull();
        assertThat(relayed.marked("round", "d1")).isTrue();
        assertThat(relayed.marked("round", "d2")).isFalse();
        assertThat(relayed.marked("ship", "d1")).isFalse();
    }

    @Test
    void setsAndClearsAMarkWithoutMovingTheTurn() {
        Turn open = Turn.none().relayed("round", "d1", "", 100);

        Turn marked = open.withMark("ship", "s1");
        Turn cleared = marked.withoutMark("round");

        assertThat(marked.mark("ship")).isEqualTo("s1");
        assertThat(marked.mark("round")).isEqualTo("d1");
        assertThat(marked.sameTurnAs(open)).isTrue();
        assertThat(cleared.mark("round")).isNull();
        assertThat(cleared.mark("ship")).isEqualTo("s1");
        assertThat(cleared.holds("round", "d1")).isTrue();
    }

    @Test
    void countsRelaysOfOneStepRunningAndStartsAgainOnAnotherStep() {
        Turn twice = Turn.none().relayed("round", "d1", "", 100).relayed("round", "d2", "", 200);
        Turn switched = twice.relayed("plan", "p1", "", 300);

        assertThat(twice.run()).containsExactly(100L, 200L);
        assertThat(switched.run()).containsExactly(300L);
    }

    @Test
    void keepsOnlyTheLastTwentyRelaysOfAStepRunning() {
        Turn turn = Turn.none();
        for (long at = 1; at <= 21; at++) {
            turn = turn.relayed("round", "d" + at, "", at);
        }

        assertThat(turn.run()).containsExactlyElementsOf(LongStream.rangeClosed(2, 21).boxed().toList());
    }

    @Test
    void readsAMissingStepDigestMarksAndRunAsNothing() {
        Turn bare = new Turn(null, 0, null, 0, null, null);

        assertThat(bare).isEqualTo(Turn.none());
    }

    @Test
    void hashesTextToTheFirstSixteenBytesOfItsSha256InHex() {
        assertThat(Turn.digestOf("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223");
    }
}

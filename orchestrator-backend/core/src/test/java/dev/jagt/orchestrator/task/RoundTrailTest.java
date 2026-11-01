package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class RoundTrailTest {

    @Test
    void appendsARoundAndKeepsTheLiftItCountsFrom() {
        RoundTrail grown = RoundTrail.liftedAt(500).withRound(new RoundTrail.Round(600, 3, "", "a", ""))
                .withRound(new RoundTrail.Round(700, 2, "", "b", ""));

        assertThat(grown.rounds()).extracting(RoundTrail.Round::digest).containsExactly("a", "b");
        assertThat(grown.since()).isEqualTo(500);
    }

    @Test
    void keepsOnlyTheLastTwentyRounds() {
        RoundTrail grown = RoundTrail.none();
        for (int i = 1; i <= 21; i++) {
            grown = grown.withRound(new RoundTrail.Round(i, 1, "", "d" + i, ""));
        }

        assertThat(grown.rounds()).extracting(RoundTrail.Round::at)
                .containsExactlyElementsOf(LongStream.rangeClosed(2, 21).boxed().toList());
    }

    @Test
    void saysTheLastRoundWasRelayedOnlyForItsOwnDigest() {
        RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 3, "", "a", ""))
                .withRound(new RoundTrail.Round(2, 3, "", "b", ""));

        assertThat(trail.relayed("b")).isTrue();
        assertThat(trail.relayed("a")).isFalse();
    }

    @Test
    void relaysNothingBeforeTheFirstRound() {
        assertThat(RoundTrail.none().relayed("")).isFalse();
    }

    @Test
    void holdsNoNewNoteOnlyWhereTheLastRoundCarriedThemAndNoChecksWereRedThenOrNow() {
        RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 2, "", "a", "t1 note_1"));
        RoundTrail redBefore = RoundTrail.none().withRound(new RoundTrail.Round(1, 2, "unit-tests", "a", "t1 note_1"));

        assertThat(trail.noNewNote("t1 note_1", "")).isTrue();
        assertThat(trail.noNewNote("t1 note_1", "unit-tests")).isFalse();
        assertThat(redBefore.noNewNote("t1 note_1", "")).isFalse();
    }

    @Test
    void holdsNewNotesWhereTheyAreKnownAndTheLastRoundDidNotCarryThem() {
        RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 2, "", "a", "t1 note_1"));

        assertThat(trail.newNotes("t1 note_2")).isTrue();
        assertThat(trail.newNotes("t1 note_1")).isFalse();
        assertThat(RoundTrail.none().newNotes("t1 note_2")).isTrue();
        assertThat(RoundTrail.none().newNotes("")).isFalse();
    }
}

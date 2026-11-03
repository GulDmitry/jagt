package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;
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

    @Test
    void tellsEachThreadNewReopenedOrUnchangedSinceTheLastRound() {
        RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 2, "", "a", "mr#note_1 11\nmr#note_2 21"));
        ReviewFacts round = new ReviewFacts(true, false, "success", "",
                List.of("mr#note_1\nbot: still wrong", "mr#note_2\nbot: drop it", "mr#note_3\nbot: rename"), 0,
                List.of(), List.of("12", "21", "31"), "", false, false);

        String since = trail.sinceLast(round);

        assertThat(since).isEqualTo("Since the last round — still open, with a new note: mr#note_1; "
                + "still open, unchanged: mr#note_2; new: mr#note_3.");
    }

    @Test
    void tellsNothingOnTheFirstRound() {
        ReviewFacts round = new ReviewFacts(true, false, "success", "", List.of("mr#note_1\nbot: rename"), 0,
                List.of(), List.of("11"), "", false, false);

        assertThat(RoundTrail.none().sinceLast(round)).isEmpty();
    }
}

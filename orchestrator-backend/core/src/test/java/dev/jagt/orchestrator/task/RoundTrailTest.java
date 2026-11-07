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
    void holdsNoNewNoteWhereTheSameNoteIdsComeWithTheirThreadsWrittenAnotherWay() {
        RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 2, "", "a",
                "https://host/abc/-/merge_requests/7#note_101 101\nsrc/Main.java:12 202"));

        assertThat(trail.noNewNote("Main.java:12 202\nsrc/Main.java:30 101", "")).isTrue();
    }

    @Test
    void holdsNoNewNoteWhereOneRequestLinkedFromTwoRepositoriesCarriesItsThreadsTwice() {
        RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 1, "", "a", "x 101"));

        assertThat(trail.noNewNote("[api] x 101\n[web] x 101", "")).isTrue();
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
    void tellsHowManyNotesAreNewAndHowManyUnchangedSinceTheLastRound() {
        RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 2, "", "a", "11\n21"));
        ReviewFacts round = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 0),
                new ReviewFacts.Pipeline("success", "", ""),
                new ReviewFacts.Discussion(List.of("mr#note_1\nbot: still wrong", "mr#note_2\nbot: drop it", "mr#note_3\nbot: rename"), List.of(), List.of("12", "21", "31")));

        String since = trail.sinceLast(round);

        assertThat(since).isEqualTo("Since the last round — new notes: 2; still open, unchanged: 1.");
    }

    @Test
    void tellsNothingOnTheFirstRound() {
        ReviewFacts round = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 0),
                new ReviewFacts.Pipeline("success", "", ""),
                new ReviewFacts.Discussion(List.of("mr#note_1\nbot: rename"), List.of(), List.of("11")));

        assertThat(RoundTrail.none().sinceLast(round)).isEmpty();
    }

    @Test
    void saysNothingOfNewNotesWhereEveryNoteIsOneTheLastRoundCarried() {
        RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 1, "", "a", "11"));
        ReviewFacts round = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 0),
                new ReviewFacts.Pipeline("success", "", ""),
                new ReviewFacts.Discussion(List.of("mr#note_1\nbot: rename"), List.of(), List.of("11")));

        assertThat(trail.sinceLast(round)).isEqualTo("Since the last round — still open, unchanged: 1.");
    }

    @Test
    void saysNothingOfUnchangedNotesWhereEveryNoteIsNew() {
        RoundTrail trail = RoundTrail.none().withRound(new RoundTrail.Round(1, 1, "", "a", "11"));
        ReviewFacts round = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 0),
                new ReviewFacts.Pipeline("success", "", ""),
                new ReviewFacts.Discussion(List.of("mr#note_2\nbot: rename"), List.of(), List.of("12")));

        assertThat(trail.sinceLast(round)).isEqualTo("Since the last round — new notes: 1.");
    }
}

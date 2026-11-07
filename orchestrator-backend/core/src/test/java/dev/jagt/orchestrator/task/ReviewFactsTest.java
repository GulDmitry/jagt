package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewFactsTest {

    @Test
    void movesAThreadWhoseNewestNoteIsTheAgentsOwnToTheOnesAwaitingTheReviewer() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("rev: fix this\nbot: done", "rev: and this"), List.of("old thread"), List.of()));

        ReviewFacts answered = facts.answeredBy("bot");

        assertThat(answered.discussion().threads()).containsExactly("rev: and this");
        assertThat(answered.discussion().awaitingReviewer()).containsExactly("old thread", "rev: fix this\nbot: done");
        assertThat(answered.request().exists()).isTrue();
        assertThat(answered.pipeline().status()).isEqualTo("success");
        assertThat(answered.request().openedAt()).isEqualTo(7);
    }

    @Test
    void keepsThePipelineAddressWhenThreadsAreMovedToTheOnesAwaitingTheReviewer() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("failed", "unit-tests", "https://host.invalid/p/1"),
                new ReviewFacts.Discussion(List.of("bot: done"), List.of(), List.of("n1")));

        assertThat(facts.answeredBy("bot").pipeline().url()).isEqualTo("https://host.invalid/p/1");
    }

    @Test
    void dropsThePipelineAddressWithTheChecksItLeftUnsaid() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("failed", "unit-tests", "https://host.invalid/p/1"),
                new ReviewFacts.Discussion(List.of(), List.of(), List.of()));

        assertThat(facts.withoutChecks().pipeline().url()).isEmpty();
    }

    @Test
    void readsTheNewestNoteAsTheLastLineAndIgnoresSurroundingBlankSpace() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("bot: first\nrev: reply\n\n"), List.of(), List.of()));

        assertThat(facts.answeredBy("bot").discussion().awaitingReviewer()).isEmpty();
    }

    @Test
    void matchesTheAccountWithoutAnAtSignOrCase() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("rev: x\n@Bot: done"), List.of(), List.of()));

        assertThat(facts.answeredBy(" @bot ").discussion().awaitingReviewer()).hasSize(1);
        assertThat(facts.answeredBy("BOT").discussion().awaitingReviewer()).hasSize(1);
    }

    @Test
    void movesNothingForABlankAccount() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of(": done"), List.of(), List.of()));

        assertThat(facts.answeredBy(" ").discussion().awaitingReviewer()).isEmpty();
    }

    @Test
    void movesNothingForANewestNoteWithNoAuthor() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("rev: x\n: bot says"), List.of(), List.of()));

        assertThat(facts.answeredBy("bot").discussion().awaitingReviewer()).isEmpty();
    }

    @Test
    void movesNothingForAnEmptyThread() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of(""), List.of(), List.of()));

        assertThat(facts.answeredBy("bot").discussion().threads()).containsExactly("");
    }

    @Test
    void readsAMissingPipelineFailureAndAwaitingListAsEmpty() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, true, false, false, 0),
                new ReviewFacts.Pipeline("failed", null, ""),
                new ReviewFacts.Discussion(List.of(), null, List.of()));

        assertThat(facts.pipeline().failure()).isEmpty();
        assertThat(facts.discussion().awaitingReviewer()).isEmpty();
    }

    @Test
    void keepsTheHostsNoteIdsWhenAThreadMovesToTheReviewer() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("l1\nrev: a", "l2\nrev: b\nbot: done", "l3\nrev: c"), List.of(), List.of("note_1", "note_3")));

        ReviewFacts answered = facts.answeredBy("bot");

        assertThat(answered.discussion().threads()).containsExactly("l1\nrev: a", "l3\nrev: c");
        assertThat(answered.discussion().newestNotes()).containsExactly("note_1", "note_3");
    }

    @Test
    void fingerprintsTheSameHostNoteIdsEquallyWhateverTheCopiedTextOrOrder() {
        ReviewFacts first = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("l2\nrev: b", "l1\nrev: a\nbot: fine"), List.of(), List.of("note_2", "note_1")));
        ReviewFacts second = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 9),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("l1\nrev: a\nbot: fine, agreed", "other\nrev: b"), List.of(), List.of("note_1", "note_2")));

        assertThat(first.noteFingerprint()).isEqualTo(second.noteFingerprint());
    }

    @Test
    void fingerprintsADifferentNoteIdDifferently() {
        ReviewFacts before = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("l1\nrev: a"), List.of(), List.of("note_1")));
        ReviewFacts after = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("l1\nrev: a"), List.of(), List.of("note_2")));

        assertThat(before.noteFingerprint()).isNotEqualTo(after.noteFingerprint());
    }

    @Test
    void fingerprintsNothingWhereTheHostGaveNoNoteId() {
        ReviewFacts facts = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("l1\nrev: a", "l2\nrev: b"), List.of(), List.of()));

        assertThat(facts.noteFingerprint()).isEmpty();
    }

    @Test
    void twoReadsWithABlankNoteIdAreNeverTheSameRound() {
        ReviewFacts first = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 7),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("l1\nrev: a"), List.of(), List.of("note_1", "")));
        ReviewFacts second = new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 9),
                new ReviewFacts.Pipeline("success", null, ""),
                new ReviewFacts.Discussion(List.of("l1\nrev: a"), List.of(), List.of("note_1", "")));

        assertThat(first.noteKey().sameAs(second.noteKey())).isFalse();
    }
}

package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewFactsTest {

    @Test
    void movesAThreadWhoseNewestNoteIsTheAgentsOwnToTheOnesAwaitingTheReviewer() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null,
                List.of("rev: fix this\nbot: done", "rev: and this"), 7, List.of("old thread"), List.of(), "", false, false);

        ReviewFacts answered = facts.answeredBy("bot");

        assertThat(answered.threads()).containsExactly("rev: and this");
        assertThat(answered.awaitingReviewer()).containsExactly("old thread", "rev: fix this\nbot: done");
        assertThat(answered.exists()).isTrue();
        assertThat(answered.pipelineStatus()).isEqualTo("success");
        assertThat(answered.openedAt()).isEqualTo(7);
    }

    @Test
    void keepsThePipelineAddressWhenThreadsAreMovedToTheOnesAwaitingTheReviewer() {
        ReviewFacts facts = new ReviewFacts(true, false, "failed", "unit-tests", List.of("bot: done"), 7, List.of(),
                List.of("n1"), "https://host.invalid/p/1", false, false);

        assertThat(facts.answeredBy("bot").pipelineUrl()).isEqualTo("https://host.invalid/p/1");
    }

    @Test
    void dropsThePipelineAddressWithTheChecksItLeftUnsaid() {
        ReviewFacts facts = new ReviewFacts(true, false, "failed", "unit-tests", List.of(), 7, List.of(), List.of(),
                "https://host.invalid/p/1", false, false);

        assertThat(facts.withoutChecks().pipelineUrl()).isEmpty();
    }

    @Test
    void readsTheNewestNoteAsTheLastLineAndIgnoresSurroundingBlankSpace() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of("bot: first\nrev: reply\n\n"), 7,
                List.of(), List.of(), "", false, false);

        assertThat(facts.answeredBy("bot").awaitingReviewer()).isEmpty();
    }

    @Test
    void matchesTheAccountWithoutAnAtSignOrCase() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of("rev: x\n@Bot: done"), 7, List.of(), List.of(), "", false, false);

        assertThat(facts.answeredBy(" @bot ").awaitingReviewer()).hasSize(1);
        assertThat(facts.answeredBy("BOT").awaitingReviewer()).hasSize(1);
    }

    @Test
    void movesNothingForABlankAccount() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of(": done"), 7, List.of(), List.of(), "", false, false);

        assertThat(facts.answeredBy(" ").awaitingReviewer()).isEmpty();
    }

    @Test
    void movesNothingForANewestNoteWithNoAuthor() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of("rev: x\n: bot says"), 7, List.of(), List.of(), "", false, false);

        assertThat(facts.answeredBy("bot").awaitingReviewer()).isEmpty();
    }

    @Test
    void movesNothingForAnEmptyThread() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of(""), 7, List.of(), List.of(), "", false, false);

        assertThat(facts.answeredBy("bot").threads()).containsExactly("");
    }

    @Test
    void readsAMissingPipelineFailureAndAwaitingListAsEmpty() {
        ReviewFacts facts = new ReviewFacts(true, true, "failed", null, List.of(), 0, null, List.of(), "", false, false);

        assertThat(facts.pipelineFailure()).isEmpty();
        assertThat(facts.awaitingReviewer()).isEmpty();
    }

    @Test
    void keepsEachNewestNoteIdWithItsThreadWhenAnotherThreadMovesToTheReviewer() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null,
                List.of("l1\nrev: a", "l2\nrev: b\nbot: done", "l3\nrev: c"), 7, List.of(),
                List.of("note_1", "note_2", "note_3"), "", false, false);

        ReviewFacts answered = facts.answeredBy("bot");

        assertThat(answered.threads()).containsExactly("l1\nrev: a", "l3\nrev: c");
        assertThat(answered.newestNotes()).containsExactly("note_1", "note_3");
    }

    @Test
    void fingerprintsTheSameOpenThreadsAndNewestNotesEquallyWhateverTheCopiedText() {
        ReviewFacts first = new ReviewFacts(true, false, "success", null,
                List.of("l2\nrev: b", "l1\nrev: a\nbot: fine"), 7, List.of(), List.of("note_2", "note_1"), "", false, false);
        ReviewFacts second = new ReviewFacts(true, false, "success", null,
                List.of("l1\nrev: a\nbot: fine, agreed", "l2\nrev: b"), 9, List.of(), List.of("note_1", "note_2"), "", false, false);

        assertThat(first.noteFingerprint()).isEqualTo(second.noteFingerprint());
    }

    @Test
    void fingerprintsADifferentNewestNoteOnTheSameThreadDifferently() {
        ReviewFacts before = new ReviewFacts(true, false, "success", null, List.of("l1\nrev: a"), 7, List.of(),
                List.of("note_1"), "", false, false);
        ReviewFacts after = new ReviewFacts(true, false, "success", null, List.of("l1\nrev: a"), 7, List.of(),
                List.of("note_2"), "", false, false);

        assertThat(before.noteFingerprint()).isNotEqualTo(after.noteFingerprint());
    }

    @Test
    void fingerprintsNothingWhereAReadMissesAThreadsNewestNoteId() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of("l1\nrev: a", "l2\nrev: b"), 7,
                List.of(), List.of("note_1"), "", false, false);

        assertThat(facts.noteFingerprint()).isEmpty();
    }

    @Test
    void twoReadsWithABlankNewestNoteIdOnOneThreadAreNeverTheSameRound() {
        ReviewFacts first = new ReviewFacts(true, false, "success", null, List.of("l1\nrev: a", "l2\nrev: b"), 7,
                List.of(), List.of("note_1", ""), "", false, false);
        ReviewFacts second = new ReviewFacts(true, false, "success", null, List.of("l1\nrev: a", "l2\nrev: b"), 9,
                List.of(), List.of("note_1", ""), "", false, false);

        assertThat(first.noteKey().sameAs(second.noteKey())).isFalse();
    }
}

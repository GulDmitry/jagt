package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewFactsTest {

    @Test
    void movesAThreadWhoseNewestNoteIsTheAgentsOwnToTheOnesAwaitingTheReviewer() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null,
                List.of("rev: fix this\nbot: done", "rev: and this"), 7, List.of("old thread"));

        ReviewFacts answered = facts.answeredBy("bot");

        assertThat(answered.threads()).containsExactly("rev: and this");
        assertThat(answered.awaitingReviewer()).containsExactly("old thread", "rev: fix this\nbot: done");
        assertThat(answered.exists()).isTrue();
        assertThat(answered.pipelineStatus()).isEqualTo("success");
        assertThat(answered.openedAt()).isEqualTo(7);
    }

    @Test
    void readsTheNewestNoteAsTheLastLineAndIgnoresSurroundingBlankSpace() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of("bot: first\nrev: reply\n\n"), 7,
                List.of());

        assertThat(facts.answeredBy("bot").awaitingReviewer()).isEmpty();
    }

    @Test
    void matchesTheAccountWithoutAnAtSignOrCase() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of("rev: x\n@Bot: done"), 7, List.of());

        assertThat(facts.answeredBy(" @bot ").awaitingReviewer()).hasSize(1);
        assertThat(facts.answeredBy("BOT").awaitingReviewer()).hasSize(1);
    }

    @Test
    void movesNothingForABlankAccount() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of(": done"), 7, List.of());

        assertThat(facts.answeredBy(" ").awaitingReviewer()).isEmpty();
    }

    @Test
    void movesNothingForANewestNoteWithNoAuthor() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of("rev: x\n: bot says"), 7, List.of());

        assertThat(facts.answeredBy("bot").awaitingReviewer()).isEmpty();
    }

    @Test
    void movesNothingForAnEmptyThread() {
        ReviewFacts facts = new ReviewFacts(true, false, "success", null, List.of(""), 7, List.of());

        assertThat(facts.answeredBy("bot").threads()).containsExactly("");
    }

    @Test
    void readsAMissingPipelineFailureAndAwaitingListAsEmpty() {
        ReviewFacts facts = new ReviewFacts(true, true, "failed", null, List.of(), 0, null);

        assertThat(facts.pipelineFailure()).isEmpty();
        assertThat(facts.awaitingReviewer()).isEmpty();
    }
}

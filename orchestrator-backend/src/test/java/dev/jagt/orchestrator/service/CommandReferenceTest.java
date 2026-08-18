package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The grammar as a human is told it: a verb kept working for muscle memory must not also be advertised, or
 * both spellings end up in circulation and the reference stops being one answer.
 */
class CommandReferenceTest {

    @Test
    void advertisesOnlyTheCurrentSpellingOfARenamedVerb() {
        assertThat(CommandReference.text()).contains("sweep <ticket>").doesNotContain("review <ticket>");
    }

    @Test
    void offersOnlyTheCurrentSpellingToThePalette() {
        assertThat(CommandReference.verbs()).extracting(CommandReference.Verb::id)
                .contains("sweep").doesNotContain("review");
    }

    @Test
    void handsThePaletteTheRetiredSpellingSoItRunsWhatTheConsoleRuns() {
        assertThat(CommandReference.verbs()).filteredOn(verb -> verb.id().equals("sweep"))
                .singleElement().extracting(CommandReference.Verb::aliases)
                .isEqualTo(List.of("review"));
    }
}

package dev.jagt.orchestrator.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandReferenceTest {

    private record Declared(String id, String hint, List<String> usage, boolean report,
                           boolean aboutOneTask) implements GlobalCommand {
        @Override
        public String run(String tail) {
            return "ran";
        }
    }

    private static final GlobalCommand SPEND = new Declared("spend", "what the calls cost",
            List.of("spend [since]", "  … today — only this session"), true, false);

    @Test
    void advertisesOnlyTheCurrentSpellingOfARenamedVerb() {
        assertThat(CommandReference.text(List.of())).contains("sweep <ticket>").doesNotContain("review <ticket>");
    }

    @Test
    void offersOnlyTheCurrentSpellingToThePalette() {
        assertThat(CommandReference.verbs(List.of())).extracting(CommandReference.Verb::id)
                .contains("sweep").doesNotContain("review");
    }

    @Test
    void handsThePaletteTheRetiredSpellingSoMuscleMemoryStillRuns() {
        assertThat(CommandReference.verbs(List.of())).filteredOn(verb -> verb.id().equals("sweep"))
                .singleElement().extracting(CommandReference.Verb::aliases)
                .isEqualTo(List.of("review"));
    }

    @Test
    void putsACommandDeclaredOnceIntoTheReferenceAndThePaletteBoth() {
        assertThat(CommandReference.text(List.of(SPEND))).contains("spend [since]", "what the calls cost");
        assertThat(CommandReference.verbs(List.of(SPEND))).filteredOn(verb -> verb.id().equals("spend"))
                .singleElement().satisfies(verb -> {
                    assertThat(verb.report()).isTrue();
                    assertThat(verb.takesTask()).isFalse();
                });
    }

    @Test
    void printsAModifierUnderTheVerbItBelongsTo() {
        List<String> lines = List.of(CommandReference.text(List.of(SPEND)).split("\n"));

        int verb = lines.indexOf(lines.stream().filter(l -> l.contains("spend [since]")).findFirst().orElseThrow());
        assertThat(lines.get(verb + 1)).contains("only this session");
    }

    @Test
    void marksAReportAboutOneTaskSoNoSurfaceOffersItWithoutOne() {
        GlobalCommand drafts = new Declared("drafts", "what one task drafted", List.of("drafts <task>"), true,
                true);

        assertThat(CommandReference.verbs(List.of(drafts))).filteredOn(verb -> verb.id().equals("drafts"))
                .singleElement().extracting(CommandReference.Verb::aboutOneTask).isEqualTo(true);
    }
}

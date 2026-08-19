package dev.jagt.orchestrator.surface.console;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tier 1 has no screen and no model: a line either parses into a command it runs, or falls through to tier 2.
 * It knows no verb of its own — what a task owns comes from {@link TaskAction}, the rest from the registry.
 */
class GrammarDispatchTest {

    private static class Spy implements GlobalCommand {
        private final String id;
        private String tail;

        Spy(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String hint() {
            return "does " + id;
        }

        @Override
        public String run(String tail) {
            this.tail = tail;
            return "ran " + id;
        }
    }

    private final Spy report = new Spy("spend");
    private final CommandService commands = mock(CommandService.class);
    private final NaturalLanguageDispatch naturalLanguage = mock(NaturalLanguageDispatch.class);
    private final GrammarDispatch grammar = new GrammarDispatch(commands,
            new GlobalCommands(List.of(report)), naturalLanguage);

    @Test
    void runsACommandNoTaskOwnsWithoutBeingTaughtItsVerb() {
        assertThat(grammar.run("spend")).isEqualTo("ran spend");
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void handsSuchACommandEverythingTypedAfterTheVerb() {
        grammar.run("spend ABC-1 from feature/parent keep the API stable");

        assertThat(report.tail).isEqualTo("ABC-1 from feature/parent keep the API stable");
    }

    @Test
    void runsTheSweepForTheVerbItWasRenamedFrom() {
        grammar.run("review ABC-1");

        verify(commands).execute("ABC-1", TaskAction.SWEEP);
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void runsAVerbTypedWithCapitalsInsteadOfPayingTheModelForTheCase() {
        grammar.run("Review ABC-1");

        verify(commands).execute("ABC-1", TaskAction.SWEEP);
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void readsTheDiffModifierWhateverCaseItWasTypedIn() {
        grammar.run("IDE ABC-1 DIFF");

        verify(commands).execute("ABC-1", TaskAction.DIFF);
    }

    @Test
    void runsTheDiffVerbTheOtherSurfacesAlreadyOffer() {
        grammar.run("diff ABC-1");

        verify(commands).execute("ABC-1", TaskAction.DIFF);
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void namesTheUsageWhenAPerTaskVerbArrivesWithoutATask() {
        assertThat(grammar.run("ship")).contains("usage: ship <ticket>");
        verifyNoInteractions(commands);
    }

    @Test
    void leavesProseToTheModelInsteadOfTakingItsFirstWordForAVerb() {
        grammar.run("compare the last two commits");

        verify(naturalLanguage).interpret("compare the last two commits");
        verifyNoInteractions(commands);
    }

    /**
     * `rev` must stay ambiguous: completing it to `revert` alone would fill in a shared-branch write for
     * someone whose fingers meant the sweep, so the retired spelling has to be in the completion vocabulary.
     */
    @Test
    void completesBothSpellingsOfARenamedVerbSoAPrefixStaysAmbiguous() {
        assertThat(grammar.completions()).contains("revert", "review", "sweep", "spend");
    }
}

package dev.jagt.orchestrator.shell;

import dev.jagt.orchestrator.model.LaunchRequest;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
import dev.jagt.orchestrator.service.StateViews;
import dev.jagt.orchestrator.service.TaskLauncher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tier 1 has no screen and no model: a line either parses into a command it runs, or falls through to tier 2.
 */
class GrammarDispatchTest {

    private final StateViews views = mock(StateViews.class);
    private final ConfigService config = mock(ConfigService.class);
    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final CommandService commands = mock(CommandService.class);
    private final NaturalLanguageDispatch naturalLanguage = mock(NaturalLanguageDispatch.class);
    private final GrammarDispatch grammar = new GrammarDispatch(views, commands, launcher, naturalLanguage,
            config);

    @Test
    void answersTheActivityVerbFromTheSameViewTheBoardServes() {
        when(views.activity()).thenReturn("what jagt did on its own — newest first");

        assertThat(grammar.run("activity")).contains("what jagt did on its own");
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
    void leavesProseToTheModelInsteadOfTakingItsFirstWordForAVerb() {
        grammar.run("compare the last two commits");

        verify(naturalLanguage).interpret("compare the last two commits");
        verifyNoInteractions(commands);
    }

    @Test
    void runsTheDiffVerbTheOtherSurfacesAlreadyOffer() {
        grammar.run("diff ABC-1");

        verify(commands).execute("ABC-1", TaskAction.DIFF);
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void treatsFreeTextAfterPlanAsNotesNotAProject() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "demo", new ProjectConfig("/a", "origin/main", "dev", List.of()),
                "sobrado", new ProjectConfig("/b", "origin/stage", "dev", List.of()))));

        LaunchRequest args = grammar.parseDoArgs(List.of("do", "ABC-2099", "plan", "walk", "me", "through", "it"));

        assertThat(args.project()).isNull();
        assertThat(args.mode()).isEqualTo("plan");
        assertThat(args.notes()).isEqualTo("walk me through it");
    }

    @Test
    void readsTheBaseBranchAfterFromAndKeepsTheRestAsNotes() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "demo", new ProjectConfig("/a", "origin/main", "dev", List.of()))));

        LaunchRequest args = grammar.parseDoArgs(
                List.of("do", "ABC-1", "from", "feature/parent", "demo", "keep the API stable"));

        assertThat(args.baseBranch()).isEqualTo("feature/parent");
        assertThat(args.project()).isEqualTo("demo");
        assertThat(args.notes()).isEqualTo("keep the API stable");
    }

    /**
     * A review request names its own source and target branch, so a ticket typed beside its URL can only
     * contradict it — and the task that came out would be a branch the request does not track, which the next
     * `ship` pushes while the request keeps waiting on the other one.
     */
    @Test
    void refusesAResumeThatTriesToNameTheTaskBesideTheRequestUrl() {

        assertThatThrownBy(() -> grammar.resume(List.of("resume", "https://host/mr/42", "ABC-9")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carries its own branches");
        verifyNoInteractions(launcher);
    }

    @Test
    void refusesFromWithoutABranchInsteadOfSwallowingTheNextWord() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

        assertThatThrownBy(() -> grammar.parseDoArgs(List.of("do", "ABC-1", "from")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("`from` needs the branch");
    }
    @Test
    void readsSeveralProjectsFromOneTokenForWorkThatSpansThem() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "demo", new ProjectConfig("/a", "origin/main", "dev", List.of()),
                "sobrado", new ProjectConfig("/b", "origin/stage", "dev", List.of()))));

        LaunchRequest args = grammar.parseDoArgs(List.of("do", "ABC-1", "demo,sobrado", "keep them in step"));

        assertThat(args.project()).isEqualTo("demo,sobrado");
        assertThat(args.notes()).isEqualTo("keep them in step");
    }

    @Test
    void keepsATokenAsNotesWhenOnlySomeOfItsCommaPartsAreProjects() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "demo", new ProjectConfig("/a", "origin/main", "dev", List.of()))));

        LaunchRequest args = grammar.parseDoArgs(List.of("do", "ABC-1", "demo,whatever"));

        assertThat(args.project()).isNull();
        assertThat(args.notes()).isEqualTo("demo,whatever");
    }
}

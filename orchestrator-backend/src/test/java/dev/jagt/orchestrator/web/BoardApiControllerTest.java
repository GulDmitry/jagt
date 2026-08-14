package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.model.LaunchRequest;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.StateViews;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.UsageTracker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BoardApiControllerTest {

    private final TaskViews taskViews = mock(TaskViews.class);
    private final CommandService commands = mock(CommandService.class);
    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final UsageTracker usageTracker = mock(UsageTracker.class);
    private final NaturalLanguageDispatch naturalLanguage = mock(NaturalLanguageDispatch.class);
    private final OrchestratorTools tools = mock(OrchestratorTools.class);
    private final StateViews views = mock(StateViews.class);
    private final BoardApiController api = new BoardApiController(taskViews, commands, launcher, configService,
            usageTracker, mock(TaskEventStream.class), naturalLanguage, tools, views);

    @Test
    void executesAnActionByTheSameNameTheConsoleTakes() {
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenReturn("ship ABC-1: approval relayed");

        assertThat(api.act("ABC-1", "ship").message()).isEqualTo("ship ABC-1: approval relayed");
    }

    /** The palette adds no rule: it hands the text to the dispatcher and returns what came back, verbatim. */
    @Test
    void passesPaletteTextToTheDispatcherAndReturnsItsAnswerUnchanged() {
        when(naturalLanguage.interpret("ship the login task"))
                .thenReturn("understood as `ship a2` — ship a2: pushed");

        assertThat(api.interpret(new BoardApiController.InterpretRequest("ship the login task")).message())
                .isEqualTo("understood as `ship a2` — ship a2: pushed");
        verifyNoInteractions(commands, launcher);
    }

    /**
     * PARITY is the rule for the two surfaces, and these are the verbs the board had no way to reach at all —
     * `resume` above all: a reopened review request, or taking over someone else's, was console-only.
     */
    @Test
    void resumesAnExistingReviewRequestLikeTheConsoleDoes() {
        when(launcher.resume("https://host/mr/42")).thenReturn("Resumed PROJ-1 on its existing branch");

        assertThat(api.resume(new BoardApiController.ResumeRequest("  https://host/mr/42  ")).message())
                .isEqualTo("Resumed PROJ-1 on its existing branch");
    }

    /** A ticket key is not a review request: there is nothing to resume from it, so it is refused, not guessed. */
    @Test
    void refusesToResumeWithoutAReviewRequestUrl() {
        assertThatThrownBy(() -> api.resume(new BoardApiController.ResumeRequest("ABC-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL is required");
        verifyNoInteractions(launcher);
    }

    /** The console makes you type `prune` before `prune all`; the board must not collapse that into one click. */
    @Test
    void prunesAsADryRunUnlessDeletionIsAsked() {
        when(tools.pruneBranches(false)).thenReturn("would delete ABC-40 (dry run)");
        when(tools.pruneBranches(true)).thenReturn("deleted 1 of 1");

        assertThat(api.prune(new BoardApiController.PruneRequest(false)).message()).contains("dry run");
        assertThat(api.prune(new BoardApiController.PruneRequest(true)).message()).contains("deleted");
    }

    /**
     * The palette completes and validates against THIS list, so a verb the console accepts and this omits is a
     * capability the board cannot express — the parity bug in miniature.
     */
    @Test
    void servesEveryVerbThePaletteMustBeAbleToCompleteAndValidate() {
        var ids = api.commands().stream().map(dev.jagt.orchestrator.service.CommandReference.Verb::id).toList();

        assertThat(ids).contains("ship", "review", "ide", "diff", "deploy", "revert", "respawn", "done", "focus",
                "do", "resume", "prune", "stats", "help");
        // Whether a verb needs a task is what decides if "ship" alone is a mistake or a command.
        assertThat(api.commands().stream()
                .filter(dev.jagt.orchestrator.service.CommandReference.Verb::takesTask)
                .map(dev.jagt.orchestrator.service.CommandReference.Verb::id))
                .contains("ship").doesNotContain("do", "prune", "help");
    }

    @Test
    void offersTheEverydayVerbsBeforeTheRareOnes() {
        var ids = api.commands().stream().map(dev.jagt.orchestrator.service.CommandReference.Verb::id).toList();

        assertThat(ids).startsWith("review", "ship", "do");
        assertThat(ids.indexOf("ship")).isLessThan(ids.indexOf("focus"));
        assertThat(ids.indexOf("deploy")).isLessThan(ids.indexOf("done"));
    }

    @Test
    void servesTheSameCommandReferenceTheConsolePrints() {
        assertThat(api.help()).isEqualTo(dev.jagt.orchestrator.service.CommandReference.text());
    }

    @Test
    void servesTheSameSpendTextTheConsolePrints() {
        when(views.usageStats()).thenReturn("assistant token spend …");

        assertThat(api.stats()).isEqualTo("assistant token spend …");
    }

    @Test
    void refusesAnUnknownActionIdRatherThanMappingItToSomethingNear() {
        assertThatThrownBy(() -> api.act("ABC-1", "shipit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown action 'shipit'");
        verifyNoInteractions(commands);
    }

    @Test
    void startsATaskThroughTheSameLauncherTheTypedCommandUses() {
        LaunchRequest posted = new LaunchRequest("ABC-42", "demo", "plan", null, "feature/parent",
                "with tests");
        when(launcher.launch(posted)).thenReturn("Task ABC-42 initialized");

        var result = api.launch(posted);

        assertThat(result.message()).isEqualTo("Task ABC-42 initialized");
    }

    @Test
    void treatsBlankModifiersAsAbsentSoAnEmptyFormFieldIsNotAProjectNamedEmptyString() {
        api.launch(new LaunchRequest("  ABC-42 ", "", "", "", "", ""));

        verify(launcher).launch(new LaunchRequest("ABC-42", null, null, null, null, null));
    }

    @Test
    void refusesALaunchWithNothingToLookUp() {
        assertThatThrownBy(() -> api.launch(new LaunchRequest(" ", null, null, null, null, null)))
                .hasMessageContaining("ticket key or a URL is required");
        verifyNoInteractions(launcher);
    }

    @Test
    void turnsARefusalIntoA400WithTheSentenceTheHumanShouldRead() {
        var response = api.refused(new IllegalStateException("ship: ABC-1 is DONE — nothing to ship onto"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "ship: ABC-1 is DONE — nothing to ship onto");
    }

    @Test
    void reportsTheSessionSpendAndTheProjectsAlongsideTheTasks() {
        when(taskViews.all()).thenReturn(List.of());
        when(usageTracker.session()).thenReturn(dev.jagt.orchestrator.model.TokenUsage.ofCall(1000, 0, 50, 0.1));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                java.util.Map.of("demo", new dev.jagt.orchestrator.model.ProjectConfig("/p", "origin/main",
                        "dev", List.of()))));

        var board = api.tasks();

        assertThat(board.spend().calls()).isEqualTo(1);
        assertThat(board.spend().tokens()).isEqualTo(1050);
        assertThat(board.projects()).containsExactly("demo");
    }
}

package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
import dev.jagt.orchestrator.service.Refusal;
import dev.jagt.orchestrator.service.TaskLauncher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A button may never do more than the console: everything here goes through the same gate a typed command does. */
class TaskCommandsControllerTest {

    private final CommandService commands = mock(CommandService.class);
    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final NaturalLanguageDispatch naturalLanguage = mock(NaturalLanguageDispatch.class);
    private final TaskCommandsController api = new TaskCommandsController(commands, launcher, naturalLanguage);
    private final RefusedRequests refusals = new RefusedRequests();

    @Test
    void executesAnActionByTheSameNameTheConsoleTakes() {
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenReturn("ship ABC-1: approval relayed");

        assertThat(api.act("ABC-1", "ship").message()).isEqualTo("ship ABC-1: approval relayed");
    }

    /**
     * A page that has been open a while asks for actions the task no longer allows. The sentence is for the
     * human; the code is what lets the page tell "your view was stale" from "jagt refuses this".
     */
    @Test
    void namesTheRefusalKindSoAStalePageCanTellItselfApartFromARealRefusal() {
        var refused = refusals.refused(new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE, "Deploy is not available"));

        assertThat(refused.getBody()).containsEntry("error", "Deploy is not available")
                .containsEntry("code", "ACTION_NOT_AVAILABLE");
        assertThat(refusals.refused(new IllegalStateException("ship: ABC-1 is DEPLOYED")).getBody())
                .containsOnlyKeys("error");
    }

    /** The palette adds no rule: it hands the text to the dispatcher and returns what came back, verbatim. */
    @Test
    void passesPaletteTextToTheDispatcherAndReturnsItsAnswerUnchanged() {
        when(naturalLanguage.interpret("ship the login task"))
                .thenReturn("understood as `ship a2` — ship a2: pushed");

        assertThat(api.interpret(new TaskCommandsController.InterpretRequest("ship the login task")).message())
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

        assertThat(api.resume(new TaskCommandsController.ResumeRequest("  https://host/mr/42  ")).message())
                .isEqualTo("Resumed PROJ-1 on its existing branch");
    }

    /** A ticket key is not a review request: there is nothing to resume from it, so it is refused, not guessed. */
    @Test
    void refusesToResumeWithoutAReviewRequestUrl() {
        assertThatThrownBy(() -> api.resume(new TaskCommandsController.ResumeRequest("ABC-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL is required");
        verifyNoInteractions(launcher);
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
        var response = refusals.refused(new IllegalStateException("ship: ABC-1 is DONE — nothing to ship onto"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "ship: ABC-1 is DONE — nothing to ship onto");
    }
}

package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.Launched;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskCommandsControllerTest {

    private final CommandService commands = mock(CommandService.class);
    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final NaturalLanguageDispatch naturalLanguage = mock(NaturalLanguageDispatch.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final TaskCommandsController api =
            new TaskCommandsController(commands, launcher, naturalLanguage, sessions);
    private final RefusedRequests refusals = new RefusedRequests();

    @Test
    void executesAnActionByTheSameNameTheConsoleTakes() {
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenReturn("ship ABC-1: approval relayed");

        assertThat(api.act("ABC-1", "ship").message()).isEqualTo("ship ABC-1: approval relayed");
    }

    @Test
    void saysALineToTheSessionOfTheTaskTheReportIsAbout() {
        when(sessions.say("a1", "no, answer 2 differently")).thenReturn("Said to the agent.");

        assertThat(api.say("a1", new TaskCommandsController.LineRequest("no, answer 2 differently")).message())
                .isEqualTo("Said to the agent.");
    }

    @Test
    void refusesAnEmptyLineRatherThanInterruptingASessionWithNothing() {
        assertThatThrownBy(() -> api.say("a1", new TaskCommandsController.LineRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nothing to say");
        verifyNoInteractions(sessions);
    }

    @Test
    void namesTheRefusalKindSoAStalePageCanTellItselfApartFromARealRefusal() {
        var refused = refusals.refused(new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE, "Deploy is not available"));

        assertThat(refused.getBody()).containsEntry("error", "Deploy is not available")
                .containsEntry("code", "ACTION_NOT_AVAILABLE");
    }

    @Test
    void carriesNoCodeForARefusalNothingOnThePageBranchesOn() {
        var refused = refusals.refused(new IllegalStateException("ship: ABC-1 is DEPLOYED"));

        assertThat(refused.getBody()).containsOnlyKeys("error");
    }

    @Test
    void passesPaletteTextToTheDispatcherAndReturnsItsAnswerUnchanged() {
        when(naturalLanguage.interpret("ship the login task"))
                .thenReturn("understood as `ship a2` — ship a2: pushed");

        assertThat(api.interpret(new TaskCommandsController.InterpretRequest("ship the login task")).message())
                .isEqualTo("understood as `ship a2` — ship a2: pushed");
        verifyNoInteractions(commands, launcher);
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
        LaunchRequest posted = LaunchRequest.of("ABC-42").withProject("demo").withMode("plan")
                .withBaseBranch("feature/parent").withNotes("with tests");
        when(launcher.launch(posted)).thenReturn(Launched.created("ABC-42", "Task ABC-42 initialized"));

        var result = api.launch(posted);

        assertThat(result.message()).isEqualTo("Task ABC-42 initialized");
    }

    @Test
    void refusesALaunchThatCreatedNoTaskLikeTheTypedDo() {
        LaunchRequest posted = LaunchRequest.of("ABC-42");
        when(launcher.launch(posted)).thenReturn(Launched.refused("error: read failed: ABC-42"));

        assertThatThrownBy(() -> api.launch(posted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("error: read failed: ABC-42");
    }

    @Test
    void treatsBlankModifiersAsAbsentSoAnEmptyFormFieldIsNotAProjectNamedEmptyString() {
        when(launcher.launch(any())).thenReturn(Launched.created("ABC-42", "Task ABC-42 initialized"));

        api.launch(new LaunchRequest("  ABC-42 ", "", "", "", "", ""));

        verify(launcher).launch(LaunchRequest.of("ABC-42"));
    }

    @Test
    void takesAFormWithNoTicketToTheLauncherRatherThanHoldingARuleOfItsOwn() {
        LaunchRequest written = LaunchRequest.defaults().withProject("demo").withNotes("split the invoice mailer");
        when(launcher.launch(written)).thenReturn(Launched.created("split-the-invoice-mailer", "initialized"));

        var result = api.launch(LaunchRequest.of(" ").withProject("demo").withNotes("split the invoice mailer"));

        assertThat(result.message()).isEqualTo("initialized");
    }

    @Test
    void turnsARefusalIntoA400WithTheSentenceTheHumanShouldRead() {
        var response = refusals.refused(new IllegalStateException("ship: ABC-1 is DONE — nothing to ship onto"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "ship: ABC-1 is DONE — nothing to ship onto");
    }
}

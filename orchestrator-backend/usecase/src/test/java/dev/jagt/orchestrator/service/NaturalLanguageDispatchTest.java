package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Refusal;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.MasterAssistant.CommandProposal;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tier 2 must never be able to do more than tier 1. Every test here is about that boundary: the model
 * PROPOSES and this class validates, so a hallucinated task, an unknown verb or a plain misunderstanding
 * ends as a sentence instead of an action.
 */
class NaturalLanguageDispatchTest {

    private final MeteredAssistant assistant = mock(MeteredAssistant.class);
    private final CommandService commands = mock(CommandService.class);
    private final TaskLauncher launcher = mock(TaskLauncher.class);

    private NaturalLanguageDispatch dispatchWith(StateService state) {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        return new NaturalLanguageDispatch(assistant, state, new TaskViews(state, config), commands, launcher);
    }

    private static StateService stateWithOneTask(Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        return state;
    }

    private void proposes(String command, String task, String ticket, String reason) {
        when(assistant.mapCommand(anyString(), anyString())).thenReturn(new Answer<>(
                Optional.of(new CommandProposal(command, task, ticket, reason)), TokenUsage.NONE));
    }

    @Test
    void answersWithTheCurrentVerbWhenTheProposalEchoedTheSpellingItWasRenamedFrom(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("review", "a1", "", "the human said review");
        when(commands.execute("ABC-1", TaskAction.SWEEP)).thenReturn("sweep ABC-1: checks success");

        String result = dispatchWith(state).interpret("what does the review say on a1");

        assertThat(result).isEqualTo("understood as `sweep ABC-1` — sweep ABC-1: checks success");
    }

    @Test
    void runsTheMappedActionThroughTheSameGateAButtonUsesAndSaysWhatItUnderstood(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("ship", "a1", "", "the only task about layout");
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenReturn("ship ABC-1: pushed");

        String result = dispatchWith(state).interpret("push the layout one for review");

        // The interpretation is stated BEFORE the outcome: a wrong mapping has to be visible to be correctable.
        assertThat(result).isEqualTo("understood as `ship ABC-1` — ship ABC-1: pushed");
    }

    /** A refusal stays a refusal — answered as text it would reach the palette as a success. */
    @Test
    void keepsTheInterpretationVisibleWhenTheGateRefusesWhatWasUnderstood(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("deploy", "a1", "", "asked to release it");
        when(commands.execute("ABC-1", TaskAction.DEPLOY)).thenThrow(new Refusal(
                Refusal.Code.ACTION_NOT_AVAILABLE, "Deploy is not available for ABC-1 (it is REVIEW_PENDING)"));

        assertThatThrownBy(() -> dispatchWith(state).interpret("put the layout one live"))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refused -> assertThat(refused.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE))
                .extracting(Throwable::getMessage)
                .isEqualTo("understood as `deploy ABC-1` — refused: Deploy is not available for ABC-1"
                        + " (it is REVIEW_PENDING)");
    }

    @Test
    void refusesATaskTheModelInventedInsteadOfActingOnSomethingNear(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("ship", "ZZZ-999", "", "guessed");

        String result = dispatchWith(state).interpret("ship the other one");

        assertThat(result).contains("not which task");
        verifyNoInteractions(commands);
    }

    @Test
    void refusesAVerbThatIsNotInTheGrammar(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("rm-rf", "a1", "", "");

        String result = dispatchWith(state).interpret("nuke it");

        assertThat(result).contains("unknown command 'rm-rf'");
        verifyNoInteractions(commands, launcher);
    }

    @Test
    void reportsTheAmbiguityWhenTheModelCouldNotChooseRatherThanPickingOne(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("none", "", "", "two tasks mention login");

        String result = dispatchWith(state).interpret("ship the login one");

        assertThat(result).contains("Not clear enough to act on: two tasks mention login");
        verifyNoInteractions(commands, launcher);
    }

    @Test
    void startsANewTaskWhenTheRequestIsADoAndCarriesTheTicketThrough(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("do", "", "ABC-42", "a new ticket");
        when(launcher.launch(LaunchRequest.of("ABC-42"))).thenReturn("Task ABC-42 initialized");

        String result = dispatchWith(state).interpret("pick up ABC-42");

        assertThat(result).isEqualTo("understood as `do ABC-42` — Task ABC-42 initialized");
    }

    /** Taking over a review request is a third way in, and the palette had no way to express it. */
    @Test
    void resumesAReviewRequestWhenTheRequestIsAUrlToOne(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("resume", "", "https://host/mr/42", "an existing merge request");
        when(launcher.resume("https://host/mr/42")).thenReturn("Resumed PROJ-1");

        assertThat(dispatchWith(state).interpret("take over this MR https://host/mr/42"))
                .isEqualTo("understood as `resume https://host/mr/42` — Resumed PROJ-1");
    }

    @Test
    void refusesToResumeWithoutAUrlBecauseThereIsNothingToTakeOver(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("resume", "", "ABC-1", "no url given");

        assertThat(dispatchWith(state).interpret("resume that thing"))
                .contains("no review-request URL was named");
        verifyNoInteractions(launcher);
    }

    @Test
    void asksForTheTicketWhenADoArrivesWithoutOne(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("do", "", "", "no ticket in the request");

        String result = dispatchWith(state).interpret("start a new task");

        assertThat(result).contains("no ticket was named");
        verifyNoInteractions(launcher);
    }

    @Test
    void saysSoWhenTheAssistantIsUnavailableInsteadOfFailingSilently(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        when(assistant.mapCommand(anyString(), anyString())).thenReturn(Answer.unavailable());

        assertThat(dispatchWith(state).interpret("do something")).contains("Could not reach the assistant");
        verifyNoInteractions(commands, launcher);
    }

    @Test
    void spendsNothingOnEmptyInput(@TempDir Path root) {
        assertThat(dispatchWith(stateWithOneTask(root)).interpret("   ")).isEqualTo("Nothing to interpret.");
        verifyNoInteractions(assistant, commands, launcher);
    }

    /** A mistyped command is the common case for reaching tier 2, and paying a model call for it is silly. */
    @Test
    void treatsASingleUnknownWordAsATypoWithoutSpendingACall(@TempDir Path root) {
        assertThat(dispatchWith(stateWithOneTask(root)).interpret("shipp"))
                .contains("Unknown command 'shipp'");
        verifyNoInteractions(assistant, commands, launcher);
    }

    @Test
    void answersARetiredVerbByNameInsteadOfLettingAModelMapItOntoALiveOne(@TempDir Path root) {
        NaturalLanguageDispatch dispatch = dispatchWith(stateWithOneTask(root));

        assertThat(dispatch.interpret("prune all")).contains("jagt has no `prune`");
        assertThat(dispatch.interpret("prune")).contains("jagt has no `prune`");
        verifyNoInteractions(assistant, commands, launcher);
    }

    /**
     * The model is told the tasks AND what is legal for each, so it does not propose a refused action. That
     * context comes from the same projection the board renders — nothing else may invent a command list.
     */
    @Test
    void tellsTheModelOnlyAboutRealTasksAndTheirLegalActions(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("none", "", "", "");

        dispatchWith(state).interpret("what is going on with the layout one");

        var context = forClass(String.class);
        verify(assistant).mapCommand(any(), context.capture());
        assertThat(context.getValue())
                .contains("id=ABC-1", "alias=a1", "status=REVIEW_PENDING", "Widget layout is off")
                .contains("legal=ship")
                .contains("- deploy:", "- revert:", "- do:");
    }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant.Answer;
import dev.jagt.orchestrator.assistant.MasterAssistant.CommandProposal;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.model.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        return new NaturalLanguageDispatch(assistant, state, new TaskViews(state), commands, launcher);
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
    void runsTheMappedActionThroughTheSameGateAButtonUsesAndSaysWhatItUnderstood(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("ship", "a1", "", "the only task about layout");
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenReturn("ship ABC-1: pushed");

        String result = dispatchWith(state).interpret("залей ту задачу с вёрсткой");

        // The interpretation is stated BEFORE the outcome: a wrong mapping has to be visible to be correctable.
        assertThat(result).isEqualTo("understood as `ship ABC-1` — ship ABC-1: pushed");
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
        when(launcher.launch("ABC-42", null, null, null, null)).thenReturn("Task ABC-42 initialized");

        String result = dispatchWith(state).interpret("возьми ABC-42 в работу");

        assertThat(result).isEqualTo("understood as `do ABC-42` — Task ABC-42 initialized");
    }

    @Test
    void asksForTheTicketWhenADoArrivesWithoutOne(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("do", "", "", "no ticket in the request");

        String result = dispatchWith(state).interpret("начни новую задачу");

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

    /**
     * The model is told the tasks AND what is legal for each, so it does not propose a refused action. That
     * context comes from the same projection the board renders — nothing else may invent a command list.
     */
    @Test
    void tellsTheModelOnlyAboutRealTasksAndTheirLegalActions(@TempDir Path root) {
        StateService state = stateWithOneTask(root);
        proposes("none", "", "", "");

        dispatchWith(state).interpret("что там с вёрсткой");

        var context = forClass(String.class);
        verify(assistant).mapCommand(any(), context.capture());
        assertThat(context.getValue())
                .contains("id=ABC-1", "alias=a1", "status=REVIEW_PENDING", "Widget layout is off")
                .contains("legal=ship")
                .contains("- deploy:", "- revert:", "- do:");
    }
}

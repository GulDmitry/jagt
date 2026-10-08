package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.Rounds;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommandAssistant.CommandProposal;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NaturalLanguageDispatchTest {

    private final CommandService commands = mock(CommandService.class);
    private final TaskLauncher launcher = mock(TaskLauncher.class);

    @Test
    void answersWithTheCurrentVerbWhenTheProposalEchoedTheSpellingItWasRenamedFrom(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> new Answer<>(
                Optional.of(new CommandProposal("review", "a1", "", "the human said review")), TokenUsage.NONE);
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);
        when(commands.execute("ABC-1", TaskAction.SWEEP)).thenReturn("sweep ABC-1: checks success");

        String result = dispatch.interpret("what does the review say on a1");

        assertThat(result).isEqualTo("understood as `sweep ABC-1` — sweep ABC-1: checks success");
    }

    @Test
    void runsTheMappedActionThroughTheSameGateAButtonUsesAndSaysWhatItUnderstood(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> new Answer<>(
                Optional.of(new CommandProposal("ship", "a1", "", "the only task about layout")), TokenUsage.NONE);
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenReturn("ship ABC-1: pushed");

        String result = dispatch.interpret("push the layout one for review");

        assertThat(result).isEqualTo("understood as `ship ABC-1` — ship ABC-1: pushed");
    }

    @Test
    void keepsTheInterpretationVisibleWhenTheGateRefusesWhatWasUnderstood(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> new Answer<>(
                Optional.of(new CommandProposal("deploy", "a1", "", "asked to release it")), TokenUsage.NONE);
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);
        when(commands.execute("ABC-1", TaskAction.DEPLOY)).thenThrow(new Refusal(
                Refusal.Code.ACTION_NOT_AVAILABLE, "Deploy is not available for ABC-1 (it is REVIEW_PENDING)"));

        assertThatThrownBy(() -> dispatch.interpret("put the layout one live"))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refused -> assertThat(refused.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE))
                .extracting(Throwable::getMessage)
                .isEqualTo("understood as `deploy ABC-1` — refused: Deploy is not available for ABC-1"
                        + " (it is REVIEW_PENDING)");
    }

    @Test
    void refusesATaskTheModelInventedInsteadOfActingOnSomethingNear(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> new Answer<>(
                Optional.of(new CommandProposal("ship", "ABC-99", "", "guessed")), TokenUsage.NONE);
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);

        String result = dispatch.interpret("ship the other one");

        assertThat(result).contains("not which task");
        verifyNoInteractions(commands);
    }

    @Test
    void refusesAVerbThatIsNotInTheGrammar(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> new Answer<>(
                Optional.of(new CommandProposal("rm-rf", "a1", "", "")), TokenUsage.NONE);
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);

        String result = dispatch.interpret("nuke it");

        assertThat(result).contains("unknown command 'rm-rf'");
        verifyNoInteractions(commands, launcher);
    }

    @Test
    void reportsTheAmbiguityWhenTheModelCouldNotChooseRatherThanPickingOne(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> new Answer<>(
                Optional.of(new CommandProposal("none", "", "", "two tasks mention login")), TokenUsage.NONE);
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);

        String result = dispatch.interpret("ship the login one");

        assertThat(result).contains("Not clear enough to act on: two tasks mention login");
        verifyNoInteractions(commands, launcher);
    }

    @Test
    void startsANewTaskWhenTheRequestIsADoAndCarriesTheTicketThrough(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> new Answer<>(
                Optional.of(new CommandProposal("do", "", "ABC-42", "a new ticket")), TokenUsage.NONE);
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);
        when(launcher.launch(LaunchRequest.of("ABC-42")))
                .thenReturn(Launched.created("ABC-42", "Task ABC-42 initialized"));

        String result = dispatch.interpret("pick up ABC-42");

        assertThat(result).isEqualTo("understood as `do ABC-42` — Task ABC-42 initialized");
    }

    @Test
    void resumesAReviewRequestWhenTheRequestIsAUrlToOne(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> new Answer<>(
                Optional.of(new CommandProposal("resume", "", "https://host/mr/42", "an existing merge request")), TokenUsage.NONE);
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);
        when(launcher.resume("https://host/mr/42")).thenReturn(Launched.created("PROJ-1", "Resumed PROJ-1"));

        assertThat(dispatch.interpret("take over this MR https://host/mr/42"))
                .isEqualTo("understood as `resume https://host/mr/42` — Resumed PROJ-1");
    }

    @Test
    void refusesToResumeWithoutAUrlBecauseThereIsNothingToTakeOver(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> new Answer<>(
                Optional.of(new CommandProposal("resume", "", "ABC-1", "no url given")), TokenUsage.NONE);
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);

        assertThat(dispatch.interpret("resume that thing"))
                .contains("no review-request URL was named");
        verifyNoInteractions(launcher);
    }

    @Test
    void asksForTheTicketWhenADoArrivesWithoutOne(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> new Answer<>(
                Optional.of(new CommandProposal("do", "", "", "no ticket in the request")), TokenUsage.NONE);
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);

        String result = dispatch.interpret("start a new task");

        assertThat(result).contains("no ticket was named");
        verifyNoInteractions(launcher);
    }

    @Test
    void saysSoWhenTheAssistantIsUnavailableInsteadOfFailingSilently(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> Answer.unavailable();
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);

        assertThat(dispatch.interpret("do something")).contains("Could not reach the assistant");
        verifyNoInteractions(commands, launcher);
    }

    @Test
    void spendsNothingOnEmptyInput(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> fail("the model was asked");
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);

        assertThat(dispatch.interpret("   ")).isEqualTo("Nothing to interpret.");
        verifyNoInteractions(commands, launcher);
    }

    @Test
    void treatsASingleUnknownWordAsATypoWithoutSpendingACall(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> fail("the model was asked");
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);

        assertThat(dispatch.interpret("shipp"))
                .contains("Unknown command 'shipp'");
        verifyNoInteractions(commands, launcher);
    }

    @Test
    void answersARetiredVerbByNameInsteadOfLettingAModelMapItOntoALiveOne(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        CommandAssistant assistant = (text, tasks) -> fail("the model was asked");
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch(assistant, state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);

        assertThat(dispatch.interpret("prune all")).contains("jagt has no `prune`");
        assertThat(dispatch.interpret("prune")).contains("jagt has no `prune`");
        verifyNoInteractions(commands, launcher);
    }

    @Test
    void tellsTheModelOnlyAboutRealTasksAndTheirLegalActions(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        NaturalLanguageDispatch dispatch = new NaturalLanguageDispatch((text, tasks) -> fail("the model was asked"),
                state, new TaskViews(state, config, new Rounds(config, new MasterReview())), commands, launcher);

        String context = dispatch.context();

        assertThat(context)
                .contains("id=ABC-1", "alias=a1", "status=REVIEW_PENDING", "Widget layout is off")
                .contains("legal=ship")
                .contains("- deploy:", "- revert:", "- do:");
    }
}

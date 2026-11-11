package dev.jagt.orchestrator.surface.board;

import static dev.jagt.orchestrator.surface.board.TestPaletteContextBuilder.paletteContext;
import static dev.jagt.orchestrator.surface.board.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.port.CommandAssistant.CommandProposal;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NaturalLanguageDispatchTest {

  private final CommandService commands = mock(CommandService.class);
  private final TaskLauncher launcher = mock(TaskLauncher.class);
  private final ConfigService config = mock(ConfigService.class);

  @TempDir Path root;

  private StateService state;
  private PaletteContext palette;

  @BeforeEach
  void setUp() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    state = stateService().withRoot(root).build();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .title("Widget layout is off")
            .build());
    palette = paletteContext().build(state, config);
  }

  @Test
  void answersWithTheCurrentVerbWhenTheProposalEchoedTheSpellingItWasRenamedFrom() {
    CommandAssistant assistant =
        new FakeProposingCommandAssistant(
            new CommandProposal("review", "a1", "", "the human said review"));
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);
    when(commands.execute("ABC-1", TaskAction.SWEEP)).thenReturn("sweep ABC-1: checks success");

    String result = dispatch.interpret("what does the review say on a1");

    assertThat(result).isEqualTo("understood as `sweep ABC-1` — sweep ABC-1: checks success");
  }

  @Test
  void runsTheMappedActionThroughTheSameGateButtonUsesAndSaysWhatItUnderstood() {
    CommandAssistant assistant =
        new FakeProposingCommandAssistant(
            new CommandProposal("ship", "a1", "", "the only task about layout"));
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);
    when(commands.execute("ABC-1", TaskAction.SHIP)).thenReturn("ship ABC-1: pushed");

    String result = dispatch.interpret("push the layout one for review");

    assertThat(result).isEqualTo("understood as `ship ABC-1` — ship ABC-1: pushed");
  }

  @Test
  void keepsTheInterpretationVisibleWhenTheGateRefusesWhatWasUnderstood() {
    CommandAssistant assistant =
        new FakeProposingCommandAssistant(
            new CommandProposal("deploy", "a1", "", "asked to release it"));
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);
    when(commands.execute("ABC-1", TaskAction.DEPLOY))
        .thenThrow(
            new Refusal(
                Refusal.Code.ACTION_NOT_AVAILABLE,
                "Deploy is not available for ABC-1 (it is REVIEW_PENDING)"));

    assertThatThrownBy(() -> dispatch.interpret("put the layout one live"))
        .asInstanceOf(type(Refusal.class))
        .satisfies(
            refused -> assertThat(refused.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE))
        .extracting(Throwable::getMessage)
        .isEqualTo(
            "understood as `deploy ABC-1` — refused: Deploy is not available for ABC-1"
                + " (it is REVIEW_PENDING)");
  }

  @Test
  void refusesTaskTheModelInventedInsteadOfActingOnSomethingNear() {
    CommandAssistant assistant =
        (text, tasks) ->
            new Answer<>(
                Optional.of(new CommandProposal("ship", "ABC-99", "", "guessed")), TokenUsage.NONE);
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);

    String result = dispatch.interpret("ship the other one");

    assertThat(result).contains("not which task");
    verifyNoInteractions(commands);
  }

  @Test
  void refusesVerbThatIsNotInTheGrammar() {
    CommandAssistant assistant =
        (text, tasks) ->
            new Answer<>(Optional.of(new CommandProposal("rm-rf", "a1", "", "")), TokenUsage.NONE);
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);

    String result = dispatch.interpret("nuke it");

    assertThat(result).contains("unknown command 'rm-rf'");
    verifyNoInteractions(commands, launcher);
  }

  @Test
  void reportsTheAmbiguityWhenTheModelCouldNotChooseRatherThanPickingOne() {
    CommandAssistant assistant =
        new FakeProposingCommandAssistant(
            new CommandProposal("none", "", "", "two tasks mention login"));
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);

    String result = dispatch.interpret("ship the login one");

    assertThat(result).contains("Not clear enough to act on: two tasks mention login");
    verifyNoInteractions(commands, launcher);
  }

  @Test
  void startsNewTaskWhenTheRequestIsDoAndCarriesTheTicketThrough() {
    CommandAssistant assistant =
        new FakeProposingCommandAssistant(new CommandProposal("do", "", "ABC-42", "a new ticket"));
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);
    when(launcher.launch(LaunchRequest.of("ABC-42")))
        .thenReturn(Launched.created("ABC-42", "Task ABC-42 initialized"));

    String result = dispatch.interpret("pick up ABC-42");

    assertThat(result).isEqualTo("understood as `do ABC-42` — Task ABC-42 initialized");
  }

  @Test
  void opensTaskAsThePalettes() {
    CommandAssistant assistant =
        new FakeProposingCommandAssistant(new CommandProposal("do", "", "ABC-42", "a new ticket"));
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);
    AtomicReference<ActionOrigin> openedBy = new AtomicReference<>();
    when(launcher.launch(LaunchRequest.of("ABC-42")))
        .thenAnswer(
            call -> {
              openedBy.set(OriginContext.current());
              return Launched.created("ABC-42", "Task ABC-42 initialized");
            });

    dispatch.interpret("pick up ABC-42");

    assertThat(openedBy.get()).isEqualTo(ActionOrigin.PALETTE);
  }

  @Test
  void resumesReviewRequestWhenTheRequestIsUrlToOne() {
    CommandAssistant assistant =
        new FakeProposingCommandAssistant(
            new CommandProposal("resume", "", "https://host/mr/42", "an existing merge request"));
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);
    when(launcher.resume("https://host/mr/42"))
        .thenReturn(Launched.created("PROJ-1", "Resumed PROJ-1"));

    assertThat(dispatch.interpret("take over this MR https://host/mr/42"))
        .isEqualTo("understood as `resume https://host/mr/42` — Resumed PROJ-1");
  }

  @Test
  void refusesToResumeWithoutUrlBecauseThereIsNothingToTakeOver() {
    CommandAssistant assistant =
        new FakeProposingCommandAssistant(
            new CommandProposal("resume", "", "ABC-1", "no url given"));
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);

    assertThat(dispatch.interpret("resume that thing")).contains("no review-request URL was named");
    verifyNoInteractions(launcher);
  }

  @Test
  void asksForTheTicketWhenDoArrivesWithoutOne() {
    CommandAssistant assistant =
        new FakeProposingCommandAssistant(
            new CommandProposal("do", "", "", "no ticket in the request"));
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);

    String result = dispatch.interpret("start a new task");

    assertThat(result).contains("no ticket was named");
    verifyNoInteractions(launcher);
  }

  @Test
  void saysSoWhenTheAssistantIsUnavailableInsteadOfFailingSilently() {
    CommandAssistant assistant = (text, tasks) -> Answer.unavailable();
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);

    assertThat(dispatch.interpret("do something")).contains("Could not reach the assistant");
    verifyNoInteractions(commands, launcher);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '"',
      value = {
        "\"   \"|Nothing to interpret.",
        "shipp|Unknown command 'shipp' — type `help` for the grammar, or say what you want in a"
            + " few words.",
        "prune all|jagt has no `prune`: cleaning up a merged branch is that one task's business,"
            + " and yours to do with git.",
        "prune|jagt has no `prune`: cleaning up a merged branch is that one task's business, and"
            + " yours to do with git."
      })
  void answersInputThatNeedsNoModelWithoutSpendingCall(String typed, String answer) {
    CommandAssistant assistant = (text, tasks) -> fail("the model was asked");
    NaturalLanguageDispatch dispatch =
        new NaturalLanguageDispatch(assistant, palette, commands, launcher);

    assertThat(dispatch.interpret(typed)).isEqualTo(answer);
    verifyNoInteractions(commands, launcher);
  }
}

package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestHeadlessClaudeBuilder.headlessClaude;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.port.assistant.CommandAssistant;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeadlessClaudeCommandAssistantTest {

  private final FakeProcesses runner = new FakeProcesses();
  private final HeadlessClaudeCommandAssistant assistant =
      new HeadlessClaudeCommandAssistant(
          new ToolLessRead(
              headlessClaude().build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class)),
              new OpenAiCompatibleModel(LocalModelProperties.off(), mock(UsageTracker.class)),
              LocalModelProperties.off()));
  private final CommandAssistant.Menu menu =
      new CommandAssistant.Menu(
          "ship <task>\nresume <url>", List.of("ship", "resume", "none"), List.of("ABC-5"));

  @Test
  void asksNothingWhereTheRequestIsBlank() {
    var answer = assistant.mapCommand("  ", menu);

    assertThat(answer.facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @Test
  void asksNothingWhereThereIsNoRequest() {
    var answer = assistant.mapCommand(null, menu);

    assertThat(answer.facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @Test
  void proposesTheCommandTheTaskTheTicketAndTheReasonTheAnswerNames() {
    runner.answering(
        new Processes.Result(
            0,
            "{\"structured_output\":{\"command\":\"ship\",\"task\":\"ABC-5\",\"ticket\":\"\","
                + "\"reason\":\"asked to send it out\"}}",
            ""));

    var answer = assistant.mapCommand("send ABC-5 out for review", menu);

    assertThat(answer.facts())
        .contains(
            new CommandAssistant.CommandProposal("ship", "ABC-5", "", "asked to send it out"));
  }

  @Test
  void putsTheRequestAndTheMenuInThePrompt() {
    runner.answering(new Processes.Result(0, "{\"structured_output\":{\"command\":\"none\"}}", ""));

    assistant.mapCommand("send ABC-5 out for review", menu);

    List<String> command = runner.command();
    assertThat(command)
        .anySatisfy(argument ->
            assertThat(argument)
                .contains("<request>send ABC-5 out for review</request>")
                .contains("ship <task>\nresume <url>"));
  }

  @Test
  void namesNoBranchWhereThePromptIsBlank() {
    var answer = assistant.nameBranch("  ");

    assertThat(answer.facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @Test
  void namesTheBranchInTheWordsTheAnswerGives() {
    runner.answering(
        new Processes.Result(0, "{\"structured_output\":{\"words\":\"upgrade linter\"}}", ""));

    var answer = assistant.nameBranch("the linter was updated, fix the warnings");

    assertThat(answer.facts()).contains("upgrade linter");
  }

  @Test
  void putsThePromptInTheBranchQuestion() {
    runner.answering(new Processes.Result(0, "{\"structured_output\":{\"words\":\"x\"}}", ""));

    assistant.nameBranch("the linter was updated, fix the warnings");

    assertThat(runner.command())
        .anySatisfy(argument ->
            assertThat(argument)
                .contains("<request>the linter was updated, fix the warnings</request>"));
  }
}

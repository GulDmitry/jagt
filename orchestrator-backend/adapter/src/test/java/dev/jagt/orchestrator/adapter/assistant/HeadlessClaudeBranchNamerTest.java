package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestHeadlessClaudeBuilder.headlessClaude;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import org.junit.jupiter.api.Test;

class HeadlessClaudeBranchNamerTest {

  private final FakeProcesses runner = new FakeProcesses();
  private final HeadlessClaudeBranchNamer namer =
      new HeadlessClaudeBranchNamer(
          new ToolLessRead(
              headlessClaude().build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class)),
              new OpenAiCompatibleModel(LocalModelProperties.off(), mock(UsageTracker.class)),
              LocalModelProperties.off()));

  @Test
  void asksNothingWhereThePromptIsBlank() {
    var answer = namer.nameBranch("  ");

    assertThat(answer.facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @Test
  void givesTheWordsTheAnswerNames() {
    runner.answering(
        new Processes.Result(0, "{\"structured_output\":{\"words\":\"upgrade linter\"}}", ""));

    var answer = namer.nameBranch("the linter was updated, fix the warnings");

    assertThat(answer.facts()).contains("upgrade linter");
  }

  @Test
  void putsThePromptInTheQuestion() {
    runner.answering(new Processes.Result(0, "{\"structured_output\":{\"words\":\"x\"}}", ""));

    namer.nameBranch("the linter was updated, fix the warnings");

    assertThat(runner.command())
        .anySatisfy(argument ->
            assertThat(argument)
                .contains("<request>the linter was updated, fix the warnings</request>"));
  }
}

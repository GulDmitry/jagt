package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.launch.LaunchRequest;
import dev.jagt.orchestrator.task.launch.Launched;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DoCommandTest {

  private final TaskLauncher launcher = mock(TaskLauncher.class);
  private final DoCommand command = new DoCommand(launcher);

  @Test
  void answersWithTheLaunchersSentenceWhenTaskIsCreated() {
    when(launcher.launchLine("ABC-9")).thenReturn(Launched.created("ABC-9", "Started ABC-9."));

    assertThat(command.run("ABC-9")).isEqualTo("Started ABC-9.");
  }

  @Test
  void refusesLaunchThatCreatedNoTaskSoTheTypedLineIsKept() {
    when(launcher.launchLine("ABC-9"))
        .thenReturn(Launched.refused("branch 'ABC-9' already exists in alpha"));

    assertThatThrownBy(() -> command.run("ABC-9"))
        .isInstanceOfSatisfying(
            Refusal.class, refused -> assertThat(refused.code()).isEqualTo(Refusal.Code.STATE))
        .hasMessage("branch 'ABC-9' already exists in alpha");
  }

  @ParameterizedTest
  @EnumSource(
      value = Launched.Reason.class,
      names = {"NOTHING_TO_DO", "NO_BRANCH_NAME"})
  void teachesTheDoGrammarWhenTheLineNeedsSayingWhatTheTaskIs(Launched.Reason reason) {
    when(launcher.launchLine("demo")).thenReturn(Launched.refused(reason, "error: refused"));

    assertThatThrownBy(() -> command.run("demo"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("error: refused")
        .hasMessageContaining(LaunchRequest.OWN_GRAMMAR);
  }

  @Test
  void offersTheModelTheTicketItMayStart() {
    assertThat(command.modelHint())
        .hasValueSatisfying(hint -> assertThat(hint).contains("NEW task"));
  }

  @Test
  void asksForTheTicketWhenTheModelNamedNone() {
    assertThat(command.runProposed("", "no ticket in the request"))
        .isEqualTo(
            "Understood as `do` but no ticket was named (no ticket in the request) — say it"
                + " explicitly: `do <ticket|url> [project]`.");
  }

  @Test
  void saysWhatItUnderstoodAndAnswersWithTheLaunchersSentence() {
    when(launcher.launch(LaunchRequest.of("ABC-42")))
        .thenReturn(Launched.created("ABC-42", "Task ABC-42 initialized"));

    assertThat(command.runProposed("ABC-42", "a new ticket"))
        .isEqualTo("understood as `do ABC-42` — Task ABC-42 initialized");
  }
}

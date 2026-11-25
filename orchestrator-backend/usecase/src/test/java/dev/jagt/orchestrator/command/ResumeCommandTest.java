package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.launch.PendingStarts;
import dev.jagt.orchestrator.task.launch.LaunchRequest;
import dev.jagt.orchestrator.task.launch.Launched;
import dev.jagt.orchestrator.task.launch.PendingStart;
import dev.jagt.orchestrator.task.launch.PendingStart.Placement;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResumeCommandTest {

  private final TaskLauncher launcher = mock(TaskLauncher.class);
  private final PendingStarts starts = mock(PendingStarts.class);
  private final PendingStart saved =
      new PendingStart("ABC-42", LaunchRequest.of("ABC-42"), 0L, Placement.PLACED);
  private final ResumeCommand command = new ResumeCommand(launcher, starts);

  @Test
  void resumesTheRequestTheUrlNames() {
    when(launcher.resume("https://host/mr/42"))
        .thenReturn(Launched.created("PROJ-1", "Resumed PROJ-1"));

    String answer = command.run("https://host/mr/42");

    verify(launcher).resume("https://host/mr/42");
    assertThat(answer).isEqualTo("Resumed PROJ-1");
  }

  @Test
  void refusesResumeThatTriesToNameTheTaskBesideTheRequestUrl() {
    assertThatThrownBy(() -> command.run("https://host/mr/42 ABC-9"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("carries its own branches");
    verifyNoInteractions(launcher);
  }

  @Test
  void refusesResumeThatCreatedNoTaskSoTheTypedLineIsKept() {
    when(launcher.resume("https://host/mr/42")).thenReturn(Launched.refused("no such request"));

    assertThatThrownBy(() -> command.run("https://host/mr/42"))
        .isInstanceOf(Refusal.class)
        .hasMessage("no such request");
  }

  @Test
  void refusesResumeWithNoUrlAtAll() {
    assertThatThrownBy(() -> command.run("ABC-9"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("usage: resume <request-url>");
    verifyNoInteractions(launcher);
  }

  @Test
  void resumesTheRequestWhateverSpacePadsItsUrl() {
    when(launcher.resume("https://host/mr/42"))
        .thenReturn(Launched.created("PROJ-1", "Resumed PROJ-1"));

    command.run("  https://host/mr/42  ");

    verify(launcher).resume("https://host/mr/42");
  }

  @Test
  void runsCutShortStartAgainFromItsKey() {
    when(starts.find("ABC-42")).thenReturn(Optional.of(saved));
    when(launcher.resumeStart("ABC-42")).thenReturn(Launched.created("ABC-42", "ABC-42 is a1"));

    assertThat(command.run(" ABC-42 ")).isEqualTo("ABC-42 is a1");
  }

  @Test
  void refusesCutShortStartThatIsRefusedAgain() {
    when(starts.find("ABC-42")).thenReturn(Optional.of(saved));
    when(launcher.resumeStart("ABC-42")).thenReturn(Launched.refused("error: no such item"));

    assertThatThrownBy(() -> command.run("ABC-42")).isInstanceOf(Refusal.class);
  }

  @Test
  void dropsCutShortStartOnDiscardAndStartsNothing() {
    when(starts.find("ABC-42")).thenReturn(Optional.of(saved));

    String answer = command.run("ABC-42 discard");

    verify(starts).end("ABC-42");
    verifyNoInteractions(launcher);
    assertThat(answer).contains("ABC-42");
  }

  @Test
  void refusesDiscardOfKeyNoStartHolds() {
    when(starts.find("ABC-42")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> command.run("ABC-42 discard"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(starts, never()).end("ABC-42");
  }

  @Test
  void refusesKeyWithWordOtherThanDiscardOrWithMoreWords() {
    assertThatThrownBy(() -> command.run("ABC-42 drop"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> command.run("ABC-42 discard now"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> command.run("")).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(launcher);
  }

  @Test
  void listsTheRequestFormAndBothStartForms() {
    assertThat(command.usage())
        .containsExactly(
            "resume <request-url>", "resume <start-key>", "resume <start-key> discard");
  }
}

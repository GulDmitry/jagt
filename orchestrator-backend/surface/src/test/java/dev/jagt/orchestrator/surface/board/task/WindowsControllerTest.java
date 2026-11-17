package dev.jagt.orchestrator.surface.board.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.scenario.OpenedWindows;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WindowsControllerTest {

  private final OpenedWindows windows = mock(OpenedWindows.class);
  private final WindowsController api = new WindowsController(windows);

  @Test
  void showsTheScreenOfAnOpenWindow() {
    when(windows.screen("login")).thenReturn(Optional.of("$ prompt"));

    assertThat(api.screen("login")).isEqualTo(new TypedLineController.Screen(true, "$ prompt"));
  }

  @Test
  void showsNoScreenForTheWindowThatIsClosed() {
    when(windows.screen("login")).thenReturn(Optional.empty());

    assertThat(api.screen("login")).isEqualTo(new TypedLineController.Screen(false, ""));
  }

  @Test
  void saysTheStrippedLineToTheWindow() {
    when(windows.say("login", "yes")).thenReturn("Said to login.");

    assertThat(api.say("login", new TypedLineController.LineRequest(" yes ")).message())
        .isEqualTo("Said to login.");
  }

  @Test
  void refusesToSayNothingToTheWindow() {
    assertThatThrownBy(() -> api.say("login", new TypedLineController.LineRequest("  ")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(windows);
  }
}

package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TypedLineControllerTest {

  private final NaturalLanguageDispatch naturalLanguage = mock(NaturalLanguageDispatch.class);
  private final AgentSessions sessions = mock(AgentSessions.class);
  private final CommandService commands = mock(CommandService.class);
  private final TypedLineController api =
      new TypedLineController(naturalLanguage, sessions, commands);

  @Test
  void saysLineToTheSessionOfTheTaskTheReportIsAbout() {
    when(sessions.say("a1", "no, answer 2 differently")).thenReturn("Said to the agent.");

    assertThat(
            api.say("a1", new TypedLineController.LineRequest("no, answer 2 differently"))
                .message())
        .isEqualTo("Said to the agent.");
  }

  @Test
  void refusesAnEmptyLineRatherThanInterruptingSessionWithNothing() {
    assertThatThrownBy(() -> api.say("a1", new TypedLineController.LineRequest("   ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nothing to say");
    verifyNoInteractions(sessions);
  }

  @Test
  void runsTheLineThroughTheRunVerbsGate() {
    when(commands.executeLine("a1", TaskAction.RUN, "ls -la"))
        .thenReturn("Run in the agent's window.");

    assertThat(api.run("a1", new TypedLineController.LineRequest(" ls -la ")).message())
        .isEqualTo("Run in the agent's window.");
  }

  @Test
  void refusesAnEmptyRunRatherThanTypingTheEscapeAlone() {
    assertThatThrownBy(() -> api.run("a1", new TypedLineController.LineRequest("  ")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(commands);
  }

  @Test
  void pressesTheNamedKeyInTheSessionOfTheTask() {
    when(sessions.press("a1", SessionHost.Key.CTRL_C)).thenReturn("Pressed CTRL_C.");

    assertThat(api.key("a1", new TypedLineController.KeyRequest("CTRL_C")).message())
        .isEqualTo("Pressed CTRL_C.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ctrl-c", "Escape", "C-c; rm", ""})
  void refusesKeyOutsideTheFixedSet(String name) {
    assertThatThrownBy(() -> api.key("a1", new TypedLineController.KeyRequest(name)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(sessions);
  }

  @Test
  void liveWindowWithBlankScreenIsHeldNotMissing() {
    when(sessions.screen("a1")).thenReturn(Optional.of(""));

    assertThat(api.screen("a1")).isEqualTo(new TypedLineController.Screen(true, ""));
  }

  @Test
  void sessionNoWindowHoldsIsNotHeld() {
    when(sessions.screen("a1")).thenReturn(Optional.empty());

    assertThat(api.screen("a1")).isEqualTo(new TypedLineController.Screen(false, ""));
  }

  @Test
  void passesPaletteTextToTheDispatcherAndReturnsItsAnswerUnchanged() {
    when(naturalLanguage.interpret("ship the login task"))
        .thenReturn("understood as `ship a2` — ship a2: pushed");

    assertThat(
            api.interpret(new TypedLineController.InterpretRequest("ship the login task"))
                .message())
        .isEqualTo("understood as `ship a2` — ship a2: pushed");
    verifyNoInteractions(sessions);
  }
}

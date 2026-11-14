package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.SessionHost;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpenedWindowsTest {

  private final SessionHost host = mock(SessionHost.class);
  private final ConfigService config = mock(ConfigService.class);
  private final OpenedWindows windows = new OpenedWindows(host, config);

  @Test
  void listsTheLoginAndTheScenariosWindowOfEachProjectThatAreOpen() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "alpha", TestProjectConfigBuilder.projectConfig().build(),
                        "beta", TestProjectConfigBuilder.projectConfig().build())));
    when(host.sessionName(any())).thenReturn("jagt");
    when(host.screenOf("jagt", "login")).thenReturn(Optional.of("code"));
    when(host.screenOf("jagt", "acceptance-alpha")).thenReturn(Optional.of(""));
    when(host.screenOf("jagt", "acceptance-beta")).thenReturn(Optional.empty());

    assertThat(windows.open()).containsExactlyInAnyOrder("login", "acceptance-alpha");
  }

  @Test
  void readsTheLoginScreenWithoutColourCodes() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(host.sessionName(any())).thenReturn("jagt");
    when(host.screenOf("jagt", "login"))
        .thenReturn(Optional.of("\u001b[1mcode ABCD-1234\u001b[0m  \n"));

    assertThat(windows.screen("login")).contains("code ABCD-1234");
  }

  @Test
  void typesTheLineIntoTheLoginWindow() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(host.sessionName(any())).thenReturn("jagt");
    when(host.nudgeTaskWindow("jagt", "login", "y")).thenReturn(true);

    assertThat(windows.say("login", "y")).isEqualTo("Said to login.");
  }

  @Test
  void refusesWindowJagtDoesNotOpenAndTypesNothing() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

    assertThatThrownBy(() -> windows.say("ABC-42", "y"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(host, never()).nudgeTaskWindow(any(), any(), any());
  }

  @Test
  void refusesToSayToLoginWindowThatIsGone() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(host.sessionName(any())).thenReturn("jagt");
    when(host.nudgeTaskWindow("jagt", "login", "y")).thenReturn(false);

    assertThatThrownBy(() -> windows.say("login", "y")).isInstanceOf(IllegalStateException.class);
  }
}

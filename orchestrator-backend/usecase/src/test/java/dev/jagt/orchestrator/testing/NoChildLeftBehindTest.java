package dev.jagt.orchestrator.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.LauncherSession;

class NoChildLeftBehindTest {

  @Test
  void failsAndKillsTheChildWhenOneIsLeftRunning() throws Exception {
    Process leaked = new ProcessBuilder("sleep", "30").start();
    NoChildLeftBehind guard = new NoChildLeftBehind(() -> Stream.of(leaked.toHandle()));

    assertThatThrownBy(() -> guard.launcherSessionClosed(mock(LauncherSession.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("left child processes behind");
    assertThat(leaked.waitFor()).isNotZero();
  }
}

package dev.jagt.orchestrator.adapter.tmux;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TmuxPropertiesTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void runsTmuxWhereNoCommandIsConfigured(String command) {
    assertThat(new TmuxProperties(command, "").command()).isEqualTo("tmux");
  }

  @Test
  void keepsTheConfiguredCommandWithoutItsPadding() {
    assertThat(new TmuxProperties(" /opt/jagt/tmux ", "").command()).isEqualTo("/opt/jagt/tmux");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void usesTheDefaultServerWhereNoSocketIsConfigured(String socket) {
    assertThat(new TmuxProperties("tmux", socket).socket()).isEmpty();
  }

  @Test
  void keepsTheConfiguredSocketWithoutItsPadding() {
    assertThat(new TmuxProperties("tmux", " jagt ").socket()).isEqualTo("jagt");
  }
}

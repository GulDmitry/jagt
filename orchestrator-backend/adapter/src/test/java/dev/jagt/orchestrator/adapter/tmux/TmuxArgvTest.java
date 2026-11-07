package dev.jagt.orchestrator.adapter.tmux;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TmuxArgvTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void addsNoFlagWhereNoSocketIsNamed(String socket) {
    assertThat(TmuxArgv.prefix("tmux", socket)).isEqualTo(List.of("tmux"));
  }

  @Test
  void namesTheServerWhereASocketIsNamed() {
    assertThat(TmuxArgv.prefix("tmux", " jagt-sock "))
        .isEqualTo(List.of("tmux", "-L", "jagt-sock"));
  }
}

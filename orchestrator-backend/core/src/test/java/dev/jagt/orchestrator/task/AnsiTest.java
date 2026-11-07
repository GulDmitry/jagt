package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnsiTest {

  @Test
  void removesColourAndCursorSequences() {
    assertThat(Ansi.strip("\u001b[1;32mok\u001b[0m\u001b[?25l done")).isEqualTo("ok done");
  }

  @Test
  void removesAWindowTitleSetByAnOperatingSystemCommand() {
    assertThat(Ansi.strip("\u001b]0;title\u0007text")).isEqualTo("text");
  }
}

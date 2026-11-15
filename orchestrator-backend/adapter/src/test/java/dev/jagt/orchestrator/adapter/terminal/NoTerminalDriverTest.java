package dev.jagt.orchestrator.adapter.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import org.junit.jupiter.api.Test;

class NoTerminalDriverTest {

  @Test
  void revealsNothingWhenNoTerminalIsChosen() {
    assertThat(new NoTerminalDriver().reveal("jagt"))
        .isEqualTo(TerminalDriver.Revealed.NO_TERMINAL);
  }
}

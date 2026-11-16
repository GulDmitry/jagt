package dev.jagt.orchestrator.adapter.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoTerminalDriverTest {

  @Test
  void revealsNothingWhenNoTerminalIsChosen() {
    assertThat(new NoTerminalDriver().reveal("jagt"))
        .isEqualTo(TerminalDriver.Revealed.NO_TERMINAL);
  }

  @Test
  void tellsTheHumanHowToAttachWhereNoWindowOpens() {
    try (FakeLogCapture log = new FakeLogCapture(NoTerminalDriver.class)) {
      new NoTerminalDriver()
          .openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

      assertThat(log.events())
          .singleElement()
          .satisfies(event -> {
            assertThat(event.getMessage()).isEqualTo("terminal auto-open disabled");
            assertThat(event.getKeyValuePairs())
                .extracting(pair -> pair.key + "=" + pair.value)
                .containsExactly("fix=tmux attach -t agents");
          });
    }
  }
}

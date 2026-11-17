package dev.jagt.orchestrator.adapter.tmux;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ControlOutputTest {

  private static final String SLASH = "\\";

  @Test
  void readsThePaneAndWhatItPrinted() {
    var output =
        ControlOutput.parse("%output %12 hi".getBytes(StandardCharsets.UTF_8)).orElseThrow();

    assertThat(output.pane()).isEqualTo("%12");
    assertThat(output.data()).isEqualTo("hi".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void restoresControlBytesTmuxWroteAsOctal() {
    var output =
        ControlOutput.parse(
                ("%output %1 a" + SLASH + "015" + SLASH + "012" + SLASH + "033b")
                    .getBytes(StandardCharsets.UTF_8))
            .orElseThrow();

    assertThat(output.data()).isEqualTo(new byte[] {'a', '\r', '\n', 0x1b, 'b'});
  }

  @Test
  void restoresTheBackslashTmuxWroteAsOctal() {
    var output =
        ControlOutput.parse(("%output %1 a" + SLASH + "134b").getBytes(StandardCharsets.UTF_8))
            .orElseThrow();

    assertThat(output.data()).isEqualTo("a\\b".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void keepsBytesAbove127AsTmuxSentThem() {
    var output = ControlOutput.parse("%output %1 ä".getBytes(StandardCharsets.UTF_8)).orElseThrow();

    assertThat(output.data()).isEqualTo("ä".getBytes(StandardCharsets.UTF_8));
  }

  @ParameterizedTest
  @CsvSource({"%begin 1 2 1", "%end 1 2 1", "%session-changed $0 s", "plain text"})
  void readsNoOtherLineAsOutput(String line) {
    assertThat(ControlOutput.parse(line.getBytes(StandardCharsets.UTF_8))).isEmpty();
  }
}

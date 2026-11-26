package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.protocol.debug.RecordTail;
import dev.jagt.orchestrator.protocol.message.PauseOrder;
import dev.jagt.orchestrator.protocol.message.TaskRef;
import org.junit.jupiter.api.Test;

class WireTest {

  @Test
  void readsBlankFieldAsAbsentInEveryMessage() {
    assertThat(Wire.absent("  ")).isNull();
    assertThat(new TaskRef(" ").taskId()).isNull();
    assertThat(new PauseOrder("", "\t").reason()).isNull();
    assertThat(new RecordTail(" ", null).record()).isNull();
  }

  @Test
  void stripsPaddedFieldInEveryMessage() {
    assertThat(Wire.absent(" p1 ")).isEqualTo("p1");
    assertThat(new TaskRef(" p1 ").taskId()).isEqualTo("p1");
    assertThat(new PauseOrder(" p1 ", " tired ").taskId()).isEqualTo("p1");
  }

  @Test
  void countsOnlyHttpAndHttpsAsWebLinks() {
    assertThat(Wire.webLink("https://host/x")).isTrue();
    assertThat(Wire.webLink("http://host/x")).isTrue();
    assertThat(Wire.webLink("file:///x")).isFalse();
  }
}

package dev.jagt.orchestrator.protocol.debug;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScreenReadTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 1001})
  void refusesLinesOutsideItsBounds(int lines) {
    List<Violation> found = new ScreenRead("ABC-42", lines).violations(MessageContext.NONE);

    assertThat(found).extracting(Violation::toString).anyMatch(text -> text.contains("lines"));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 1000})
  void acceptsLinesAtItsBounds(int lines) {
    List<Violation> found = new ScreenRead("ABC-42", lines).violations(MessageContext.NONE);

    assertThat(found).isEmpty();
  }

  @Test
  void readsFiftyLinesWhenNoneAreAsked() {
    assertThat(new ScreenRead("ABC-42", null).count()).isEqualTo(50);
  }
}

package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FailureCausesTest {

  @Test
  void noLogLineTakesItsCauseFromMessageThatMayBeNull() {
    Pattern messageAsCause =
        Pattern.compile("addKeyValue\\(\\s*\"cause\",[^;]*?\\.getMessage\\(\\)");

    assertThat(
            SourceRoots.javaFiles()
                .filter(path -> messageAsCause.matcher(SourceRoots.read(path)).find()))
        .isEmpty();
  }
}

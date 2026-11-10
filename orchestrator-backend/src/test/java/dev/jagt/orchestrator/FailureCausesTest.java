package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FailureCausesTest {

  @Test
  void noLogLineTakesItsCauseFromMessageThatMayBeNull() {
    Pattern messageAsCause =
        Pattern.compile("addKeyValue\\(\\s*\"cause\",[^;]*?\\.getMessage\\(\\)");

    assertThat(
            SourceRoots.javaFiles().filter(path -> messageAsCause.matcher(read(path)).find()))
        .isEmpty();
  }

  private static String read(Path source) {
    try {
      return Files.readString(source);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}

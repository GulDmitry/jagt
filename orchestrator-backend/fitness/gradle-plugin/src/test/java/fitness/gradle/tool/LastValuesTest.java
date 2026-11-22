package fitness.gradle.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LastValuesTest {

  @TempDir Path directory;

  @Test
  void readsNoValueWhenTheFileIsEmpty() throws IOException {
    Files.writeString(directory.resolve("suite"), "");

    assertThat(new LastValues(directory).read("suite")).isEmpty();
  }

  @Test
  void readsNoValueWhenTheFileIsCorrupt() throws IOException {
    Files.writeString(directory.resolve("suite"), "12x");

    assertThat(new LastValues(directory).read("suite")).isEmpty();
  }
}

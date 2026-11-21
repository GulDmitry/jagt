package fitness.junit;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.run.Block;
import fitness.core.run.FitnessRun;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LibraryChecksItselfTest {

  @Test
  void everyDefaultFitnessFunctionPassesOverTheLibraryItself() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    List<String> failures =
        FitnessRun.run(
            Path.of("..", "fitness.yml"), Block.ALL, new PrintStream(out, true, StandardCharsets.UTF_8));

    assertThat(failures).isEmpty();
  }
}

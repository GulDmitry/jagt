package fitness.junit;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Block;
import fitness.core.FitnessRun;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FitnessRunTest {

  private static final String FUNCTIONS =
      """
      schema: 1
      technical:
        maintainability:
          modularity:
            docs: { engine: scan, glob: "*.md", per: name, governs: size, max: 0 }
          testability:
            texts: { engine: scan, glob: "*.txt", per: name, governs: size, max: 5 }
      """;

  @Test
  void printsEachFitnessFunctionOfTheBlockWithItsValueAndThresholdAndNoOther(@TempDir Path directory)
      throws IOException {
    Files.writeString(directory.resolve("a.md"), "");
    Files.writeString(directory.resolve("fitness.yml"), FUNCTIONS);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    List<String> failures =
        FitnessRun.run(
            directory.resolve("fitness.yml"),
            Block.of("technical.maintainability.testability"),
            new PrintStream(out, true, StandardCharsets.UTF_8));

    assertThat(failures).isEmpty();
    assertThat(out.toString(StandardCharsets.UTF_8))
        .contains("fitness: texts: value 0, max 5", "fitness: 1 functions, 0 failed")
        .doesNotContain("docs");
  }

  @Test
  void returnsTheReportOfARuleOverItsThreshold(@TempDir Path directory) throws IOException {
    Files.writeString(directory.resolve("a.md"), "");
    Files.writeString(directory.resolve("fitness.yml"), FUNCTIONS);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    List<String> failures =
        FitnessRun.run(
            directory.resolve("fitness.yml"),
            Block.ALL,
            new PrintStream(out, true, StandardCharsets.UTF_8));

    assertThat(failures).singleElement().asString().contains("docs: value 1, max 0");
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("fitness: 2 functions, 1 failed");
  }
}

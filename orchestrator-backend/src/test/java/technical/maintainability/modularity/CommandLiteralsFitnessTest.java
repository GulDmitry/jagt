package technical.maintainability.modularity;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Direction;
import fitness.core.FitnessFunction;
import fitness.core.Project;
import fitness.core.Threshold;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandLiteralsFitnessTest {

  private static final FitnessFunction FUNCTION =
      new FitnessFunction(
          "command-literals-in-surface",
          "technical.maintainability.modularity",
          FitnessFunction.FITNESS_ENGINE,
          "count",
          new Threshold(Direction.MAX, 0),
          Map.of(),
          null,
          Map.of());

  @Test
  void namesSurfaceLiteralEqualToGlobalCommandIdOrTaskActionVerbAndNothingElse(
      @TempDir Path backend) throws IOException {
    Path commands = backend.resolve("usecase/src/main/java/dev/jagt/orchestrator/command");
    Path surface = backend.resolve("surface/src/main/java/dev/jagt/orchestrator/surface");
    Files.createDirectories(commands);
    Files.createDirectories(surface);
    Files.writeString(
        commands.resolve("ZapCommand.java"),
        """
        class ZapCommand implements GlobalCommand {
          public String id() {
            return "zap";
          }
        }
        """);
    Files.writeString(
        surface.resolve("Menu.java"),
        """
        class Menu {
          // "zap" in a comment is not a literal
          String a = "zap";
          String b = "ship";
          String c = "zapper";
          String d = "code";
        }
        """);

    var measurement =
        new CommandLiteralsFitness()
            .measure(FUNCTION, new Project(backend, "", List.of("all"), Map.of()));

    assertThat(measurement.items())
        .satisfiesExactly(
            first -> assertThat(first).contains("Menu.java").endsWith("\"zap\""),
            second -> assertThat(second).contains("Menu.java").endsWith("\"ship\""));
  }
}

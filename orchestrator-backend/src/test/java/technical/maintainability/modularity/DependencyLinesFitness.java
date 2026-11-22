package technical.maintainability.modularity;

import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class DependencyLinesFitness implements Fitness {

  private static final Path BACKEND = Path.of(".");
  private static final List<String> SKIPPED = List.of("build", "bin", ".gradle");
  private static final Pattern DEPENDENCY =
      Pattern.compile(
          "^\\s+([a-z][A-Za-z]*)?" + "([Aa]pi|[Ii]mplementation|[Cc]ompileOnly|[Rr]untimeOnly)\\s");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        files()
            .filter(path -> path.getFileName().toString().equals("build.gradle"))
            .flatMap(path ->
                lines(path)
                    .filter(line -> DEPENDENCY.matcher(line).find())
                    .map(line -> BACKEND.relativize(path) + " " + line.trim()))
            .toList());
  }

  private static Stream<Path> files() {
    try (Stream<Path> walk = Files.walk(BACKEND)) {
      return walk
          .filter(Files::isRegularFile)
          .filter(path -> !isSkipped(BACKEND.relativize(path)))
          .toList()
          .stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean isSkipped(Path relative) {
    for (Path part : relative) {
      if (SKIPPED.contains(part.toString())) {
        return true;
      }
    }
    return false;
  }

  private static Stream<String> lines(Path file) {
    try {
      return Files.readAllLines(file).stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

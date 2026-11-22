package technical.maintainability.analysability;

import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class SourceSetsFitness implements Fitness {

  private static final Path BACKEND = Path.of(".");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    try (Stream<Path> modules = Files.list(BACKEND)) {
      return Measurement.of(
          Stream.concat(Stream.of(BACKEND), modules)
              .filter(module -> !module.getFileName().toString().equals("build-logic"))
              .map(module -> module.resolve("src"))
              .filter(Files::isDirectory)
              .flatMap(SourceSetsFitness::setsUnder)
              .toList());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Stream<String> setsUnder(Path src) {
    try (Stream<Path> sets = Files.list(src)) {
      return sets
          .filter(Files::isDirectory)
          .filter(set -> !set.getFileName().toString().startsWith("."))
          .map(set -> src.getParent() + "/" + set.getFileName())
          .toList()
          .stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

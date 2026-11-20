package technical.maintainability.modularity;

import static java.util.stream.Collectors.toSet;

import dev.jagt.orchestrator.RingFiles;
import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class RingSourcesFitness implements Fitness {

  private static final Pattern TYPE_NAME = Pattern.compile("\\b[A-Z]\\w+");
  private static final Pattern FOREIGN_IMPORT =
      Pattern.compile(
          "^import (?:static )?(?!java\\.|dev\\.jagt\\.statechart\\.|org\\.jspecify\\.)",
          Pattern.MULTILINE);

  private static final Map<String, String> OUTER =
      Map.of(
          "statechart", "core usecase adapter surface .",
          "core", "usecase adapter surface .",
          "usecase", "adapter surface .");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        Stream.of(springInCentre(), outerTypesInInner(), statechartImports())
            .flatMap(items -> items)
            .toList());
  }

  private static Stream<String> springInCentre() {
    return List.of("flow", "task", "port", "protocol").stream()
        .filter(
            ring -> RingFiles.sources(ring).anyMatch(text -> text.contains("org.springframework")))
        .map(ring -> "ring " + ring + " names org.springframework");
  }

  private static Stream<String> outerTypesInInner() {
    return OUTER.entrySet().stream()
        .flatMap(module -> {
          Set<String> outerTypes =
              Stream.of(module.getValue().split(" "))
                  .flatMap(outer -> RingFiles.inModule(outer, true))
                  .map(path -> path.getFileName().toString().replace(".java", ""))
                  .collect(toSet());
          return RingFiles.inModule(module.getKey(), false)
              .filter(path ->
                  TYPE_NAME
                      .matcher(SourceRoots.read(path))
                      .results()
                      .map(MatchResult::group)
                      .anyMatch(outerTypes::contains))
              .map(path -> module.getKey() + " names an outer type: " + path.getFileName());
        });
  }

  private static Stream<String> statechartImports() {
    return RingFiles.inModule("statechart", true)
        .filter(path -> FOREIGN_IMPORT.matcher(SourceRoots.read(path)).find())
        .map(Path::getFileName)
        .map(name -> "statechart imports outside the JDK: " + name);
  }
}

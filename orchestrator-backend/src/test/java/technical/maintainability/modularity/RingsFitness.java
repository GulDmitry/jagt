package technical.maintainability.modularity;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import dev.jagt.orchestrator.ArchItems;
import dev.jagt.orchestrator.MainClasses;
import dev.jagt.orchestrator.RingFiles;
import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class RingsFitness implements Fitness {

  private static final Map<String, String> CENTRE =
      Map.of(
          "task", "task",
          "port", "task port",
          "flow", "task port flow",
          "protocol", "task port flow protocol");

  private static final List<String> BETWEEN =
      List.of("capability", "command", "job", "notify", "service", "surface", "config", "startup");

  private static final List<String> CHECKED =
      List.of(
          "flow",
          "task",
          "port",
          "protocol",
          "capability",
          "command",
          "job",
          "notify",
          "service",
          "surface",
          "adapter",
          "config",
          "startup");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        Stream.of(
                CENTRE.entrySet().stream()
                    .flatMap(ring -> items(centre(ring.getKey(), ring.getValue()))),
                BETWEEN.stream().flatMap(ring -> items(namesNoEdge(ring))),
                items(freeOfCycles()),
                unreadRings())
            .flatMap(items -> items)
            .toList());
  }

  private static ArchRule centre(String ring, String maySee) {
    return classes()
        .that()
        .resideInAPackage(RingFiles.packageOf(ring))
        .should()
        .onlyDependOnClassesThat(
            resideOutsideOfPackage(SourceRoots.BASE_PACKAGE + "..")
                .or(
                    resideInAnyPackage(
                        Stream.of(maySee.split(" "))
                            .map(RingFiles::packageOf)
                            .toArray(String[]::new))));
  }

  private static ArchRule namesNoEdge(String ring) {
    return noClasses()
        .that()
        .resideInAPackage(RingFiles.packageOf(ring))
        .should()
        .dependOnClassesThat()
        .resideInAPackage(RingFiles.packageOf("adapter"));
  }

  private static ArchRule freeOfCycles() {
    return slices().matching(SourceRoots.BASE_PACKAGE + ".(*)..").should().beFreeOfCycles();
  }

  private static Stream<String> items(ArchRule rule) {
    return ArchItems.of(rule, MainClasses.CODE).items().stream();
  }

  private static Stream<String> unreadRings() {
    Stream<String> roots =
        RingFiles.ROOTS.stream()
            .filter(root -> !Files.isDirectory(root))
            .map(root -> "source root " + root + " is not a directory");
    Stream<String> rings =
        CHECKED.stream()
            .filter(ring -> RingFiles.sources(ring).findAny().isEmpty())
            .map(ring -> "no java files in ring " + ring);
    return Stream.concat(roots, rings);
  }
}

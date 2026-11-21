package fitness.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fitness.core.FitnessConfigException;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModuleIsolationTest {

  private final ArchUnitEngine engine = new ArchUnitEngine();
  private final FitnessFunction function =
      new TestFitnessFunctionBuilder()
          .select("set", "main")
          .select("measure", "module-isolation")
          .build();

  @Test
  void namesTheReferenceTheTwoModulesAndTheTypeWhenAModuleExposesNothing() {
    Project project =
        new Project(
            Path.of(".."),
            "fitness",
            List.of("all"),
            Map.of("core", List.of(), "engine-archunit", List.of()));

    Measurement found = engine.measure(function, project);

    assertThat(found.items())
        .contains("engine-archunit -> core: fitness.archunit.ArchUnitEngine -> fitness.core.Engine");
  }

  @Test
  void findsNothingWhenTheReferencedPackageIsExposed() {
    Project project =
        new Project(
            Path.of(".."),
            "fitness",
            List.of("all"),
            Map.of("core", List.of("fitness.core", "fitness.core.run"), "engine-archunit", List.of()));

    Measurement found = engine.measure(function, project);

    assertThat(found.items()).isEmpty();
  }

  @Test
  void findsNothingWhenAModuleExposesEveryPackage() {
    Project project =
        new Project(
            Path.of(".."),
            "fitness",
            List.of("all"),
            Map.of("core", List.of(".."), "engine-archunit", List.of()));

    Measurement found = engine.measure(function, project);

    assertThat(found.items()).isEmpty();
  }

  @Test
  void failsWhenNoModuleIsDeclared() {
    Project project = new Project(Path.of(".."), "fitness", List.of("all"), Map.of());

    assertThatThrownBy(() -> engine.measure(function, project))
        .isInstanceOf(FitnessConfigException.class);
  }
}

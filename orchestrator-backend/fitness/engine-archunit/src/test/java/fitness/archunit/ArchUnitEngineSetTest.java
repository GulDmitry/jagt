package fitness.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Direction;
import fitness.core.Measurement;
import fitness.core.Project;
import fitness.core.FitnessFunction;
import fitness.core.Threshold;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchUnitEngineSetTest {

  private final ArchUnitEngine engine = new ArchUnitEngine();
  private final Project project = new Project(Path.of("."), "fitness.archunit", List.of("all"), Map.of());

  @Test
  void theMainSetLeavesOutTestClasses() {
    FitnessFunction function =
        new FitnessFunction(
            "r",
            "s",
            "archunit",
            "count",
            new Threshold(Direction.MAX, 0),
            Map.of("kind", "class", "set", "main"),
            null,
            Map.of());

    Measurement found = engine.measure(function, project);

    assertThat(found.items()).doesNotContain("fitness.archunit.ArchUnitEngineSetTest");
  }

  @Test
  void theTestSetHoldsOnlyTestClasses() {
    FitnessFunction function =
        new FitnessFunction(
            "r",
            "s",
            "archunit",
            "count",
            new Threshold(Direction.MAX, 0),
            Map.of("kind", "class", "set", "test"),
            null,
            Map.of());

    Measurement found = engine.measure(function, project);

    assertThat(found.items())
        .contains("fitness.archunit.ArchUnitEngineSetTest")
        .doesNotContain("fitness.archunit.ArchUnitEngine");
  }
}

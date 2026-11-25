package fitness.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Direction;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import fitness.core.Threshold;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchUnitEngineTest {

  private final ArchUnitEngine engine = new ArchUnitEngine();
  private final Project project = new Project(Path.of("."), "fitness", List.of("all"), Map.of());

  @Test
  void listsTheInterfacesOfAPackageAndItsSubpackages() {
    FitnessFunction function =
        new FitnessFunction(
            "r",
            "s",
            "archunit",
            "count",
            new Threshold(Direction.MAX, 0),
            Map.of("kind", "class", "set", "test", "interface", true, "package", "archunit.."),
            null,
            Map.of());

    Measurement found = engine.measure(function, project);

    assertThat(found.items()).contains("fitness.archunit.Marked");
  }

  @Test
  void anEmptyBasePackageMeasuresEveryPackage() {
    FitnessFunction function =
        new TestFitnessFunctionBuilder()
            .select("package", "fitness.archunit..")
            .select("interface", true)
            .build();

    Measurement found =
        engine.measure(function, new Project(Path.of("."), "", List.of("all"), Map.of()));

    assertThat(found.items()).contains("fitness.archunit.Marked");
  }

  @Test
  void listsTheWidestConstructorOfAnnotatedAndMetaAnnotatedClassesOverTheLimit() {
    FitnessFunction function =
        new FitnessFunction(
            "r",
            "s",
            "archunit",
            "coupling",
            new Threshold(Direction.MAX, 0),
            Map.of(
                "kind", "class",
                "set", "test",
                "annotated","fitness.archunit.Marked",
                "measure", "constructor-parameters"),
            null,
            Map.of());

    Measurement found = engine.measure(function, project);

    assertThat(found.value()).isEqualTo(3);
    assertThat(found.items())
        .containsExactly("1 fitness.archunit.ViaStereotype", "3 fitness.archunit.WidelyBuilt");
  }

  @Test
  void listsTheConcreteTypesAssignableToASupertypeButNotItself() {
    FitnessFunction function =
        new FitnessFunction(
            "r",
            "s",
            "archunit",
            "count",
            new Threshold(Direction.MAX, 0),
            Map.of("kind", "class", "interface", false, "assignableTo", "core.Engine"),
            null,
            Map.of());

    Measurement found = engine.measure(function, project);

    assertThat(found.items()).containsExactly("fitness.archunit.ArchUnitEngine");
  }

  @Test
  void listsAnInterfaceWhoseOnlyImplementerSharesItsPackage() {
    FitnessFunction function =
        new TestFitnessFunctionBuilder()
            .select("interface", true)
            .select("measure", "single-implementer")
            .build();

    Measurement found = engine.measure(function, project);

    assertThat(found.items()).containsExactly("fitness.archunit.Lone");
  }

  @Test
  void listsEveryBooleanParameterOfAStereotypedClassAndOfAPortInterface() {
    FitnessFunction function =
        new TestFitnessFunctionBuilder()
            .select("measure", "flag-arguments")
            .select("annotated", "fitness.archunit.Stereotype")
            .build();

    Measurement found = engine.measure(function, project);

    assertThat(found.items())
        .containsExactly(
            "fitness.archunit.Flagged#tell(1)",
            "fitness.archunit.Flagged#tell(2)",
            "fitness.archunit.port.FlaggedPort#launch(0)",
            "fitness.archunit.port.inner.FlaggedInnerPort#launch(0)");
  }
}

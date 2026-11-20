package fitness.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Measurement;
import fitness.core.Project;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchUnitEngineMembersTest {

  private final ArchUnitEngine engine = new ArchUnitEngine();
  private final Project project = new Project(Path.of("."), "fitness", List.of("all"));

  static final class Counting {

    static int total;
    static final ArrayList<String> NAMES = new ArrayList<>();
    static final String LABEL = "label";

    @Marked
    void tick() {
      total++;
    }
  }

  static final class Clocked {

    String zone() {
      return ZoneId.systemDefault().getId();
    }

    Instant at() {
      return Instant.now(Clock.systemUTC());
    }

    LocalDate today() {
      return LocalDate.now();
    }
  }

  @Test
  void listsStaticFieldsThatAreNotFinalOrOfAMutableType() {
    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("kind", "field")
                .select("static", true)
                .select("mutable", true)
                .build(),
            project);

    assertThat(found.items())
        .hasSize(2)
        .anyMatch(item -> item.contains("Counting.total"))
        .anyMatch(item -> item.contains("Counting.NAMES"));
  }

  @Test
  void listsTheMethodsCarryingAnAnnotation() {
    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("kind", "method")
                .select("annotated", "fitness.archunit.Marked")
                .build(),
            project);

    assertThat(found.items()).containsExactly("fitness.archunit.ArchUnitEngineMembersTest$Counting.tick()");
  }

  @Test
  void listsTheCallsToANamedMethodOfAType() {
    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder().select("calls", "java.time.ZoneId.systemDefault").build(),
            project);

    assertThat(found.items()).hasSize(1).allMatch(item -> item.contains("Clocked.zone"));
  }

  @Test
  void listsTheCallsToAMethodOfAPackageThatTakeNoClock() {
    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("calls", "java.time.*.now")
                .select("without", "java.time.Clock")
                .build(),
            project);

    assertThat(found.items()).hasSize(1).allMatch(item -> item.contains("Clocked.today"));
  }

  @Test
  void leavesOutTheClassesNamedInExcept() {
    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("calls", "java.time.ZoneId.systemDefault")
                .select("except", "Clocked,Other")
                .build(),
            project);

    assertThat(found.items()).isEmpty();
  }

  @Test
  void leavesOutTheClassesOfAPackageNamedInExcept() {
    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("calls", "java.time.ZoneId.systemDefault")
                .select("except", "archunit..")
                .build(),
            project);

    assertThat(found.items()).isEmpty();
  }
}

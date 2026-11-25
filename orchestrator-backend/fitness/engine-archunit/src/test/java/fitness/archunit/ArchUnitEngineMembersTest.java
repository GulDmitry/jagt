package fitness.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Measurement;
import fitness.core.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchUnitEngineMembersTest {

  private static final String SOURCES =
      """
      package fitness.archunit.sample;

      import java.time.Clock;
      import java.time.Instant;
      import java.time.LocalDate;
      import java.time.ZoneId;
      import java.util.ArrayList;

      @interface Marked {}

      final class Counting {
        static int total;
        static final ArrayList<String> NAMES = new ArrayList<>();
        static final String LABEL = "label";

        @Marked
        void tick() {
          total++;
        }
      }

      final class Clocked {
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
      """;

  @TempDir Path root;

  private final ArchUnitEngine engine = new ArchUnitEngine();

  @BeforeEach
  void compileTheSampleClasses() throws IOException {
    Files.createDirectories(root.resolve("src/test/java"));
    Path classes = Files.createDirectories(root.resolve("build/classes/java/test"));
    Path source = Files.writeString(root.resolve("Sample.java"), SOURCES);
    ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString());
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
            new Project(root, "fitness", List.of("all"), Map.of()));

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
                .select("annotated", "fitness.archunit.sample.Marked")
                .build(),
            new Project(root, "fitness", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("fitness.archunit.sample.Counting.tick()");
  }

  @Test
  void listsTheCallsToANamedMethodOfAType() {
    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder().select("calls", "java.time.ZoneId.systemDefault").build(),
            new Project(root, "fitness", List.of("all"), Map.of()));

    assertThat(found.items()).hasSize(1).allMatch(item -> item.contains("Clocked.zone"));
  }

  @Test
  void listsTheCallsToEveryMethodOfACommaSeparatedList() {
    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("calls", "java.time.ZoneId.systemDefault, java.time.LocalDate.now")
                .build(),
            new Project(root, "fitness", List.of("all"), Map.of()));

    assertThat(found.items())
        .hasSize(2)
        .anyMatch(item -> item.contains("Clocked.zone"))
        .anyMatch(item -> item.contains("Clocked.today"));
  }

  @Test
  void listsTheCallsToAMethodOfAPackageThatTakeNoClock() {
    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("calls", "java.time.*.now")
                .select("without", "java.time.Clock")
                .build(),
            new Project(root, "fitness", List.of("all"), Map.of()));

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
            new Project(root, "fitness", List.of("all"), Map.of()));

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
            new Project(root, "fitness", List.of("all"), Map.of()));

    assertThat(found.items()).isEmpty();
  }
}

package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Section;
import fitness.core.load.ReservedTree;
import fitness.core.run.Evaluation;
import fitness.core.run.Report;
import fitness.junit.FitnessTests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class FitnessTest {

  @TestFactory
  Stream<DynamicNode> everyRuleOfFitnessYmlHoldsItsNumber() {
    List<DynamicNode> dimensions = FitnessTests.from(Path.of("../fitness.yml")).toList();

    assertThat(dimensions).isNotEmpty();

    return dimensions.stream();
  }

  @Test
  void everyFitnessClassSitsInThePackageNamedBySomeSectionOfTheReservedTree() {
    List<String> sections = ReservedTree.sections().stream().map(Section::packageName).toList();

    assertThat(
            ServiceLoader.load(Fitness.class).stream().map(found -> found.type().getPackageName()))
        .isNotEmpty()
        .allSatisfy(name -> assertThat(sections).describedAs(name).contains(name));
  }

  @Test
  void theClockRulesScanEverySourceSet() throws IOException {
    List<String> clockRows =
        Files.readAllLines(Path.of("../fitness.yml")).stream()
            .filter(line ->
                line.contains("calls: java.time.Clock.system")
                    || line.contains("calls: java.lang.System.currentTimeMillis"))
            .toList();

    assertThat(clockRows)
        .hasSize(3)
        .allSatisfy(row -> assertThat(row).containsPattern("set: (&clockSet all|\\*clockSet),"))
        .allSatisfy(
            row -> assertThat(row).containsPattern("except: (&clockExcept \"|\\*clockExcept)"));
  }

  @Test
  void sleepAddedToMainBeyondCeilingFailsNamingRow(@TempDir Path root) throws IOException {
    String row =
        Files.readAllLines(Path.of("../fitness.yml")).stream()
            .filter(line -> line.contains("sleep-in-main:"))
            .findFirst()
            .orElseThrow()
            .strip();
    Path source = root.resolve("backend/src/main/java/p/Waits.java");
    Files.createDirectories(source.getParent());
    String sleeps = "%s.sleep(1);\n".formatted("Thread").repeat(5);
    Files.writeString(
        source,
        """
        class Waits {
        void w() throws Exception {
        %s}
        }
        """
            .formatted(sleeps));
    Files.writeString(
        root.resolve("fitness.yml"),
        "schema: 1\nproject: { root: backend, basePackage: p, sourceSets: all }\n"
            + "technical: { maintainability: { testability: { "
            + row
            + " } } }\n");

    Evaluation evaluation = Evaluation.load(root.resolve("fitness.yml"));
    FitnessFunction function = evaluation.config().functions().getFirst();

    assertThat(new Report(function, evaluation.measure(function)).failure())
        .hasValueSatisfying(failure -> assertThat(failure).contains("sleep-in-main", "value 5"));
  }
}

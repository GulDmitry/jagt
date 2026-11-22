package fitness.core.load;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.FitnessFunction;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FitnessLoaderToolsTest {

  @Test
  void readsOnlyTheRulesABuildToolMeasuresAndIgnoresTheOthersTheBuildHasNoEngineFor() {
    FitnessConfig config =
        FitnessLoader.forTools()
            .parse(
                """
                schema: 1
                technical:
                  maintainability:
                    modularity:
                      ports: { engine: archunit, governs: count, max: 5 }
                      domain: { fitness: NoSuchClass, governs: count, max: 0 }
                    analysability:
                      checkstyle: { engine: tool, tool: checkstyle, measure: warnings, governs: linter, max: 0 }
                """,
                Path.of("."));

    assertThat(config.functions()).extracting(FitnessFunction::id).containsExactly("checkstyle");
  }

  @Test
  void keepsTheGradleTaskNamesOfAToolFitnessFunction() {
    FitnessConfig config =
        FitnessLoader.forTools()
            .parse(
                """
                schema: 1
                operational:
                  performance-efficiency:
                    time-behaviour:
                      gate: { engine: tool, tool: gradle, task: "a,b", per: gate, measure: minutes, governs: size, max: 10 }
                """,
                Path.of("."));

    assertThat(config.functions().getFirst().selectors()).containsEntry("task", "a,b");
  }
}

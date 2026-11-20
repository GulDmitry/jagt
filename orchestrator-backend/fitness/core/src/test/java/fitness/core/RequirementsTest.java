package fitness.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequirementsTest {

  private static final Presence NOTHING = need -> false;
  private static final String FUNCTION = "technical.maintainability.modularity.ports";

  @ParameterizedTest(name = "a function whose runner lacks {0} fails before it runs")
  @ValueSource(strings = {"archunit", "pmd", "junit"})
  void aMissingLibraryFailsNamingTheFitnessFunctionAndTheLibrary(String library) {
    FitnessFunction function = new TestFitnessFunctionBuilder().build();

    assertThatThrownBy(() -> Requirements.check(List.of(function), Map.of(), NOTHING, Set.of(library)))
        .isInstanceOfSatisfying(
            FitnessConfigException.class,
            thrown -> {
              assertThat(thrown.reason()).isEqualTo(Reason.MISSING_TOOL);
              assertThat(thrown.key()).isEqualTo(FUNCTION + " needs " + library);
            });
  }

  @ParameterizedTest(name = "a tool row whose {0} is not installed fails before it runs")
  @ValueSource(strings = {"checkstyle", "jacoco", "pit", "osv-scanner"})
  void aMissingToolFailsNamingTheFitnessFunctionAndTheTool(String tool) {
    FitnessFunction function = new TestFitnessFunctionBuilder().engine("tool").selector("tool", tool).build();

    assertThatThrownBy(() -> Requirements.check(List.of(function), Map.of(), NOTHING, Set.of()))
        .isInstanceOfSatisfying(
            FitnessConfigException.class,
            thrown -> {
              assertThat(thrown.reason()).isEqualTo(Reason.MISSING_TOOL);
              assertThat(thrown.key()).isEqualTo(FUNCTION + " needs " + tool);
            });
  }

  @ParameterizedTest(name = "a row naming the {0} engine fails when no provider is found")
  @ValueSource(strings = {"archunit", "scan"})
  void aMissingEngineFailsNamingTheFitnessFunctionAndTheEngine(String engine) {
    String yaml =
        "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: "
            + engine
            + ", governs: count, max: 3 } } } }";
    FitnessLoader loader = new FitnessLoader(List.of(), List.of());

    assertThatThrownBy(() -> loader.parse(yaml, Path.of(".")))
        .isInstanceOfSatisfying(
            FitnessConfigException.class,
            thrown -> {
              assertThat(thrown.reason()).isEqualTo(Reason.MISSING_ENGINE);
              assertThat(thrown.key()).isEqualTo(FUNCTION + " needs " + engine);
            });
  }

  @Test
  void anOptionalRowWithAMissingToolIsReportedAndSkipped() {
    FitnessFunction function =
        new TestFitnessFunctionBuilder().engine("tool").selector("tool", "osv-scanner").selector("optional", true).build();

    List<Missing> skipped = Requirements.check(List.of(function), Map.of(), NOTHING, Set.of());

    assertThat(skipped).containsExactly(new Missing(function, "osv-scanner"));
  }

  @Test
  void aGradleRowNeedsNoToolOfItsOwn() {
    FitnessFunction function = new TestFitnessFunctionBuilder().engine("tool").selector("tool", "gradle").build();

    assertThat(Requirements.check(List.of(function), Map.of(), NOTHING, Set.of())).isEmpty();
  }

  @Test
  void theClasspathOfThisBuildHoldsJunit() {
    assertThat(Requirements.onClasspath().has("junit")).isTrue();
  }

  @Test
  void anOptionalKeyMustBeABoolean() {
    String yaml =
        "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: tool, tool: x, optional: sure, governs: count, max: 3 } } } }";

    assertThatThrownBy(() -> new FitnessLoader(List.of(), List.of()).parse(yaml, Path.of(".")))
        .isInstanceOfSatisfying(
            FitnessConfigException.class, thrown -> assertThat(thrown.reason()).isEqualTo(Reason.WRONG_TYPE));
  }
}

package fitness.core.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fitness.core.FakeEngine;
import fitness.core.FitnessConfigException;
import fitness.core.Reason;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import technical.maintainability.analysability.FakeFitness;

class FitnessLoaderRejectsTest {

  private static final String FUNCTION = "technical.maintainability.modularity.ports";

  static Stream<Arguments> causes() {
    return Stream.of(
        Arguments.of(
            "an unknown key",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, max: 3, bogus: 1 } } } }",
            Reason.UNKNOWN_KEY,
            FUNCTION + ".bogus"),
        Arguments.of(
            "a threshold of the wrong type",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, max: ten } } } }",
            Reason.WRONG_TYPE,
            FUNCTION + ".max"),
        Arguments.of(
            "a function without a threshold",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count } } } }",
            Reason.MISSING_THRESHOLD,
            FUNCTION),
        Arguments.of(
            "both thresholds",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, max: 3, min: 1 } } } }",
            Reason.BOTH_THRESHOLDS,
            FUNCTION),
        Arguments.of(
            "an unknown function id",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { fitness: Nope, governs: count, max: 3 } } } }",
            Reason.UNKNOWN_ID,
            "Nope"),
        Arguments.of(
            "a selector value outside the engine's grammar",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, max: 3, per: banana } } } }",
            Reason.UNKNOWN_SELECTOR,
            FUNCTION + ".per"),
        Arguments.of(
            "a selector the engine does not read",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, max: 3, glob: x } } } }",
            Reason.UNKNOWN_SELECTOR,
            FUNCTION + ".glob"),
        Arguments.of(
            "an engine no provider serves",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: nobody, governs: count, max: 3 } } } }",
            Reason.MISSING_ENGINE,
            FUNCTION + " needs nobody"),
        Arguments.of(
            "a function without governs",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, max: 3 } } } }",
            Reason.MISSING_KEY,
            FUNCTION + ".governs"),
        Arguments.of(
            "a function naming neither an engine nor a fitness",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { governs: count, max: 3 } } } }",
            Reason.MISSING_KEY,
            FUNCTION + ".engine"),
        Arguments.of(
            "a base file that does not exist",
            "schema: 1\nextends: nope",
            Reason.UNREADABLE,
            "extends"),
        Arguments.of("a project key outside the project keys", "schema: 1\nproject: { x: 1 }", Reason.UNKNOWN_KEY, "project.x"),
        Arguments.of(
            "source sets that are neither text nor a list",
            "schema: 1\nproject: { sourceSets: 5 }",
            Reason.WRONG_TYPE,
            "project.sourceSets"),
        Arguments.of(
            "an axis outside the axes",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, max: 3, axes: { bogus: x } } } } }",
            Reason.UNKNOWN_KEY,
            FUNCTION + ".axes.bogus"),
        Arguments.of(
            "an axis value outside its axis",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, max: 3, axes: { scope: banana } } } } }",
            Reason.UNKNOWN_SELECTOR,
            FUNCTION + ".axes.scope"),
        Arguments.of(
            "a section outside the tree",
            "schema: 1\ntechnical: { maintainability: { bogus: {} } }",
            Reason.UNKNOWN_SECTION,
            "technical.maintainability.bogus"),
        Arguments.of(
            "a dimension outside the tree",
            "schema: 1\nbogus: {}",
            Reason.UNKNOWN_SECTION,
            "bogus"),
        Arguments.of(
            "a governs outside the list",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: magic, max: 3 } } } }",
            Reason.UNKNOWN_GOVERNS,
            "magic"),
        Arguments.of(
            "a function on a characteristic",
            "schema: 1\ntechnical: { maintainability: { ports: { engine: fake, governs: count, max: 3 } } }",
            Reason.MISPLACED_FUNCTION,
            "technical.maintainability.ports"),
        Arguments.of(
            "a function id used twice",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, max: 3 } }, reusability: { ports: { engine: fake, governs: count, max: 3 } } } }",
            Reason.DUPLICATE_ID,
            "ports"),
        Arguments.of(
            "a fitness class outside its section's package",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { fitness: FakeFitness, governs: count, max: 3 } } } }",
            Reason.WRONG_PACKAGE,
            FakeFitness.class.getName()),
        Arguments.of(
            "a module declared without an exposes list",
            "schema: 1\nproject: { modules: { core: { exposes: [..] }, adapter: {} } }",
            Reason.MODULE_WITHOUT_EXPOSES,
            "project.modules.adapter"),
        Arguments.of("a schema other than 1", "schema: 2", Reason.UNSUPPORTED_SCHEMA, "schema"),
        Arguments.of("text that is not YAML", "schema: [", Reason.UNREADABLE, "fitness.yml"));
  }

  @ParameterizedTest(name = "rejects {0}")
  @MethodSource("causes")
  void rejectsBeforeAnyFitnessFunctionRuns(String cause, String yaml, Reason reason, String key) {
    FitnessLoader loader =
        new FitnessLoader(List.of(new FakeEngine()), List.of(new FakeFitness()));

    assertThatThrownBy(() -> loader.parse(yaml, Path.of(".")))
        .isInstanceOfSatisfying(
            FitnessConfigException.class,
            thrown -> {
              assertThat(thrown.reason()).isEqualTo(reason);
              assertThat(thrown.key()).isEqualTo(key);
            });
  }

  @Test
  void rejectsAFileThatDoesNotExist() {
    FitnessLoader loader = new FitnessLoader(List.of(new FakeEngine()), List.of());

    assertThatThrownBy(() -> loader.load(Path.of("absent-fitness.yml")))
        .isInstanceOfSatisfying(
            FitnessConfigException.class,
            thrown -> {
              assertThat(thrown.reason()).isEqualTo(Reason.UNREADABLE);
              assertThat(thrown.key()).isEqualTo("absent-fitness.yml");
            });
  }

  @Test
  void rejectsTwoFitnessClassesWithOneId() {
    assertThatThrownBy(() -> new FitnessLoader(List.of(), List.of(new FakeFitness(), new FakeFitness())))
        .isInstanceOfSatisfying(
            FitnessConfigException.class,
            thrown -> {
              assertThat(thrown.reason()).isEqualTo(Reason.DUPLICATE_ID);
              assertThat(thrown.key()).isEqualTo("FakeFitness");
            });
  }

  @Test
  void rejectsTwoEnginesWithOneName() {
    assertThatThrownBy(() -> new FitnessLoader(List.of(new FakeEngine(), new FakeEngine()), List.of()))
        .isInstanceOfSatisfying(
            FitnessConfigException.class,
            thrown -> {
              assertThat(thrown.reason()).isEqualTo(Reason.DUPLICATE_ENGINE);
              assertThat(thrown.key()).isEqualTo("fake");
            });
  }
}

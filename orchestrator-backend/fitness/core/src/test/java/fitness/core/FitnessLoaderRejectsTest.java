package fitness.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
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
            "an unknown engine",
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: nobody, governs: count, max: 3 } } } }",
            Reason.UNKNOWN_SELECTOR,
            FUNCTION + ".engine"),
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
}

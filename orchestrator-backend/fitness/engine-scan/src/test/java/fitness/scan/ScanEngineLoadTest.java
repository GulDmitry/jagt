package fitness.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fitness.core.FitnessConfigException;
import fitness.core.Reason;
import fitness.core.load.FitnessLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ScanEngineLoadTest {

  private static final String FUNCTION = "technical.maintainability.modularity.r";
  private static final String PREFIX =
      "schema: 1\ntechnical: { maintainability: { modularity: { r: { engine: scan, governs: count, max: 0, ";
  private static final String SUFFIX = " } } } }";

  static Stream<Arguments> causes() {
    return Stream.of(
        Arguments.of("a per line without a regex", PREFIX + "glob: x, per: line" + SUFFIX, Reason.MISSING_KEY, FUNCTION + ".regex"),
        Arguments.of("a per match without a regex", PREFIX + "glob: x, per: match" + SUFFIX, Reason.MISSING_KEY, FUNCTION + ".regex"),
        Arguments.of("a per section without a regex", PREFIX + "glob: x, per: section, measure: lines" + SUFFIX, Reason.MISSING_KEY, FUNCTION + ".regex"),
        Arguments.of("neither a glob nor a set", PREFIX + "per: name" + SUFFIX, Reason.MISSING_KEY, FUNCTION + ".glob"),
        Arguments.of("a measure without a per", PREFIX + "glob: x, measure: lines" + SUFFIX, Reason.MISSING_KEY, FUNCTION + ".per"),
        Arguments.of("a measure the per does not size", PREFIX + "glob: x, per: method, measure: lines" + SUFFIX, Reason.UNKNOWN_SELECTOR, FUNCTION + ".measure"),
        Arguments.of("a set naming no source set", PREFIX + "set: mian" + SUFFIX, Reason.UNKNOWN_SELECTOR, FUNCTION + ".set"),
        Arguments.of("a per match regex without a capturing group", PREFIX + "glob: x, per: match, regex: \"abc\"" + SUFFIX, Reason.UNKNOWN_SELECTOR, FUNCTION + ".regex"),
        Arguments.of("a regex that does not compile", PREFIX + "glob: x, regex: \"(\"" + SUFFIX, Reason.UNKNOWN_SELECTOR, FUNCTION + ".regex"));
  }

  @ParameterizedTest(name = "rejects {0}")
  @MethodSource("causes")
  void rejectsBeforeAnyFitnessFunctionRuns(
      String cause, String yaml, Reason reason, String key, @TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("src/main/java"));
    FitnessLoader loader = new FitnessLoader(List.of(new ScanEngine()), List.of());

    assertThatThrownBy(() -> loader.parse(yaml, root))
        .isInstanceOfSatisfying(
            FitnessConfigException.class,
            thrown -> {
              assertThat(thrown.reason()).isEqualTo(reason);
              assertThat(thrown.key()).isEqualTo(key);
            });
  }
}

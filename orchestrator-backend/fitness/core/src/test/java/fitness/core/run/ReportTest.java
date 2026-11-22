package fitness.core.run;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Direction;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.TestFitnessFunctionBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReportTest {

  @Test
  void aFailingFitnessFunctionNamesItsIdValueThresholdAndEveryItem() {
    FitnessFunction function = new TestFitnessFunctionBuilder().governs("size").threshold(Direction.MAX, 0).build();
    Report report = new Report(function, Measurement.of(List.of("b.Two", "a.One")));

    String failure = report.failure().orElseThrow();

    assertThat(failure).contains("ports", "value 2", "max 0", "a.One", "b.Two");
  }

  @Test
  void aFailingFitnessFunctionCarriesItsHint() {
    FitnessFunction function =
        new TestFitnessFunctionBuilder()
            .governs("size")
            .threshold(Direction.MAX, 0)
            .hint("compress it, then run the evals")
            .build();
    Report report = new Report(function, Measurement.of(List.of("a.One")));

    String failure = report.failure().orElseThrow();

    assertThat(failure).contains("max 0 — compress it, then run the evals");
  }

  @Test
  void aCountedKindBelowItsNumberAsksToLowerIt() {
    FitnessFunction function = new TestFitnessFunctionBuilder().threshold(Direction.MAX, 5).build();
    Report report = new Report(function, Measurement.of(List.of("a", "b", "c", "d")));

    String failure = report.failure().orElseThrow();

    assertThat(failure).contains("lower max to 4");
  }

  @ParameterizedTest(name = "{0} {1} {2} measured {3} passes: {4}")
  @CsvSource({
    "count, MAX, 5, 5, true",
    "count, MAX, 5, 6, false",
    "size, MAX, 5, 3, true",
    "size, MAX, 5, 6, false",
    "size, MIN, 3, 3, true",
    "size, MIN, 3, 2, false",
    "count, MAX, 0, 0, true"
  })
  void judgesAValueAgainstItsThresholdAndDirection(
      String governs, Direction direction, long limit, long value, boolean passes) {
    FitnessFunction function = new TestFitnessFunctionBuilder().governs(governs).threshold(direction, limit).build();
    Report report = new Report(function, new Measurement(value, List.of()));

    assertThat(report.failure().isEmpty()).isEqualTo(passes);
  }
}

package fitness.archunit;

import fitness.core.Direction;
import fitness.core.FitnessFunction;
import fitness.core.Threshold;
import java.util.LinkedHashMap;
import java.util.Map;

final class TestFitnessFunctionBuilder {

  private final Map<String, Object> selectors = new LinkedHashMap<>();

  TestFitnessFunctionBuilder() {
    selectors.put("set", "test");
    selectors.put("package", "archunit..");
  }

  TestFitnessFunctionBuilder select(String key, Object value) {
    selectors.put(key, value);
    return this;
  }

  FitnessFunction build() {
    return new FitnessFunction(
        "r",
        "technical.maintainability.testability",
        "archunit",
        "count",
        new Threshold(Direction.MAX, 0),
        selectors,
        null,
        Map.of());
  }
}

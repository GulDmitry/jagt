package fitness.scan;

import fitness.core.Direction;
import fitness.core.FitnessFunction;
import fitness.core.Threshold;
import java.util.LinkedHashMap;
import java.util.Map;

final class TestFitnessFunctionBuilder {

  private final Map<String, Object> selectors = new LinkedHashMap<>();
  private long limit;

  TestFitnessFunctionBuilder select(String key, Object value) {
    selectors.put(key, value);
    return this;
  }

  TestFitnessFunctionBuilder limit(long limit) {
    this.limit = limit;
    return this;
  }

  FitnessFunction build() {
    return new FitnessFunction(
        "r",
        "technical.maintainability.modularity",
        "scan",
        "count",
        new Threshold(Direction.MAX, limit),
        selectors,
        null,
        Map.of());
  }
}

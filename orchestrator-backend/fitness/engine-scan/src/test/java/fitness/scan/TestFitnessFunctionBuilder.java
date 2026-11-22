package fitness.scan;

import fitness.core.Direction;
import fitness.core.FitnessFunction;
import fitness.core.Threshold;
import java.util.LinkedHashMap;
import java.util.Map;

final class TestFitnessFunctionBuilder {

  private final Map<String, Object> selectors = new LinkedHashMap<>();
  private long limit;
  private Direction direction = Direction.MAX;

  TestFitnessFunctionBuilder select(String key, Object value) {
    selectors.put(key, value);
    return this;
  }

  TestFitnessFunctionBuilder limit(long limit) {
    this.limit = limit;
    return this;
  }

  TestFitnessFunctionBuilder floor(long limit) {
    this.limit = limit;
    this.direction = Direction.MIN;
    return this;
  }

  FitnessFunction build() {
    return new FitnessFunction(
        "r",
        "technical.maintainability.modularity",
        "scan",
        "count",
        new Threshold(direction, limit),
        selectors,
        null,
        Map.of());
  }
}

package fitness.core;

import java.util.LinkedHashMap;
import java.util.Map;

final class TestFitnessFunctionBuilder {

  private String id = "ports";
  private String engine = "fake";
  private String governs = "count";
  private Direction direction = Direction.MAX;
  private long limit;
  private final Map<String, Object> selectors = new LinkedHashMap<>();

  TestFitnessFunctionBuilder id(String id) {
    this.id = id;
    return this;
  }

  TestFitnessFunctionBuilder governs(String governs) {
    this.governs = governs;
    return this;
  }

  TestFitnessFunctionBuilder threshold(Direction direction, long limit) {
    this.direction = direction;
    this.limit = limit;
    return this;
  }

  TestFitnessFunctionBuilder engine(String engine) {
    this.engine = engine;
    return this;
  }

  TestFitnessFunctionBuilder selector(String key, Object value) {
    selectors.put(key, value);
    return this;
  }

  TestFitnessFunctionBuilder hint(String hint) {
    selectors.put("hint", hint);
    return this;
  }

  FitnessFunction build() {
    return new FitnessFunction(
        id,
        "technical.maintainability.modularity",
        engine,
        governs,
        new Threshold(direction, limit),
        selectors,
        null,
        Map.of());
  }
}

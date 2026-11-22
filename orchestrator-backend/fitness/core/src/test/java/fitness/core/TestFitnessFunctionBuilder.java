package fitness.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TestFitnessFunctionBuilder {

  private String id = "ports";
  private String engine = "fake";
  private String governs = "count";
  private Direction direction = Direction.MAX;
  private long limit;
  private final Map<String, Object> selectors = new LinkedHashMap<>();

  public TestFitnessFunctionBuilder id(String id) {
    this.id = id;
    return this;
  }

  public TestFitnessFunctionBuilder governs(String governs) {
    this.governs = governs;
    return this;
  }

  public TestFitnessFunctionBuilder threshold(Direction direction, long limit) {
    this.direction = direction;
    this.limit = limit;
    return this;
  }

  public TestFitnessFunctionBuilder engine(String engine) {
    this.engine = engine;
    return this;
  }

  public TestFitnessFunctionBuilder selector(String key, Object value) {
    selectors.put(key, value);
    return this;
  }

  public TestFitnessFunctionBuilder hint(String hint) {
    selectors.put("hint", hint);
    return this;
  }

  public FitnessFunction build() {
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

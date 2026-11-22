package fitness.core;

import java.util.Map;
import java.util.Set;

public interface Engine {

  String name();

  /** Each selector key the engine reads, with its allowed values; an empty set accepts any value. */
  Map<String, Set<String>> vocabulary();

  /** The libraries the engine needs to measure the function, by name; one that is missing fails the load. */
  default Set<String> needs(FitnessFunction function) {
    return Set.of();
  }

  /** A class each library the engine needs is recognised by, keyed by the name {@link #needs} gives it. */
  default Map<String, String> libraries() {
    return Map.of();
  }

  /** Fails the load with a {@link FitnessConfigException} when the function cannot be measured, before any function runs. */
  default void validate(FitnessFunction function, Project project) {}

  Measurement measure(FitnessFunction function, Project project);
}

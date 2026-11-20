package fitness.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FakeEngine implements Engine {

  @Override
  public String name() {
    return "fake";
  }

  @Override
  public Map<String, Set<String>> vocabulary() {
    return Map.of("per", Set.of("file", "line"));
  }

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(List.of("b", "a"));
  }
}

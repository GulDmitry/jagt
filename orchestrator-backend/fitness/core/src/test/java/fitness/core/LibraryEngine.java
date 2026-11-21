package fitness.core;

import java.util.Map;
import java.util.Set;

public record LibraryEngine(String library, String probe) implements Engine {

  @Override
  public String name() {
    return "library";
  }

  @Override
  public Map<String, Set<String>> vocabulary() {
    return Map.of();
  }

  @Override
  public Map<String, String> libraries() {
    return Map.of(library, probe);
  }

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    throw new UnsupportedOperationException();
  }
}

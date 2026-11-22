package fitness.core;

import java.util.List;

public record Measurement(long value, List<String> items) {

  public static Measurement of(List<String> items) {
    List<String> sorted = items.stream().sorted().toList();
    return new Measurement(sorted.size(), sorted);
  }
}

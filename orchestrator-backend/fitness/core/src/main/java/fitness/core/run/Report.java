package fitness.core.run;

import fitness.core.Direction;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Threshold;
import java.util.Optional;

public record Report(FitnessFunction function, Measurement measurement) {

  public Optional<String> failure() {
    Threshold threshold = function.threshold();
    long value = measurement.value();
    if (threshold.direction() == Direction.MAX && value > threshold.limit()) {
      return Optional.of(describe("max", ""));
    }
    if (threshold.direction() == Direction.MIN && value < threshold.limit()) {
      return Optional.of(describe("min", ""));
    }
    if (function.ratchets() && value < threshold.limit()) {
      return Optional.of(describe("max", ", lower max to " + value + " in fitness.yml (0176)"));
    }
    return Optional.empty();
  }

  private String describe(String direction, String advice) {
    String items = String.join(System.lineSeparator() + "  ", measurement.items());
    Object hint = function.selectors().get("hint");
    return "%s: value %d, %s %d%s%s%n  %s"
        .formatted(
            function.id(),
            measurement.value(),
            direction,
            function.threshold().limit(),
            advice,
            hint == null ? "" : " — " + hint,
            items);
  }
}

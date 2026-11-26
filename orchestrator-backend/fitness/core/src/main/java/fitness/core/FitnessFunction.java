package fitness.core;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record FitnessFunction(
    String id,
    String section,
    String engine,
    String governs,
    Threshold threshold,
    Map<String, Object> selectors,
    @Nullable String decision,
    Map<String, String> axes) {

  public static final String FITNESS_ENGINE = "fitness";
  public static final String TOOL_ENGINE = "tool";
  public static final String COUNT = "count";

  public boolean ratchets() {
    return COUNT.equals(governs)
        && threshold.direction() == Direction.MAX
        && threshold.limit() > 0
        && !Boolean.FALSE.equals(selectors.get("ratchet"));
  }

  public boolean optional() {
    return Boolean.TRUE.equals(selectors.get("optional"));
  }

  public boolean measuredByTool() {
    return TOOL_ENGINE.equals(engine);
  }
}

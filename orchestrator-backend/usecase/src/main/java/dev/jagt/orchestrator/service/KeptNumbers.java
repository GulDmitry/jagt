package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A live read keeps the last valid value of a number written outside its range, and the board's
 * head names it until a read finds the key valid or unwritten.
 */
@Component
@RequiredArgsConstructor
public class KeptNumbers {

  private static final String DRIVER = "config";

  private final Reach reach;
  private final Map<String, Integer> valid = new ConcurrentHashMap<>();

  /** The section with each refused number put back to its last valid value or removed. */
  Optional<Object> of(Object section, ConfigFile parsed) {
    List<ConfigRanges.Refusal> refusals = ConfigRanges.refusals(parsed);
    ConfigRanges.keys().forEach(key -> settle(key, parsed, refusals));
    Object kept = section;
    for (ConfigRanges.Refusal refusal : refusals) {
      kept = with(kept, List.of(refusal.key().split("\\.")), valid.get(refusal.key()));
      mark(refusal);
    }
    return refusals.isEmpty() ? Optional.empty() : Optional.of(kept);
  }

  private void settle(String key, ConfigFile parsed, List<ConfigRanges.Refusal> out) {
    boolean refused = out.stream().anyMatch(refusal -> refusal.key().equals(key));
    Integer written = ConfigRanges.written(key, parsed);
    if (!refused) {
      reach.reached(DRIVER, "orchestrator." + key);
    }
    if (written == null) {
      valid.remove(key);
    } else if (!refused) {
      valid.put(key, written);
    }
  }

  private void mark(ConfigRanges.Refusal refusal) {
    Integer keeps = valid.get(refusal.key());
    reach.lost(
        new ReachLost(
            DRIVER,
            "orchestrator." + refusal.key(),
            List.of(),
            "is "
                + refusal.value()
                + ": it must be "
                + refusal.range()
                + "; the read keeps "
                + (keeps == null ? "its default" : keeps)));
  }

  private static Object with(Object section, List<String> path, @Nullable Integer value) {
    if (!(section instanceof Map<?, ?> keys)) {
      return section;
    }
    Map<Object, Object> copy = new LinkedHashMap<>(keys);
    String head = path.get(0);
    if (path.size() == 1) {
      if (value == null) {
        copy.remove(head);
      } else {
        copy.put(head, value);
      }
    } else {
      Object below = keys.get(head);
      copy.put(head, below == null ? Map.of() : with(below, path.subList(1, path.size()), value));
    }
    return copy;
  }
}

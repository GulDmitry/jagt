package dev.jagt.gradle.fitnessraises;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.yaml.snakeyaml.Yaml;

/** Names each fitness row whose ceiling rose or floor fell without a decision record for it. */
public final class FitnessRaises {

  private FitnessRaises() {}

  /**
   * A row absent from the base is new. A loosening passes only when the row's decision changed to
   * one {@code recordChanged} confirms.
   */
  public static List<String> loosened(
      String file, String baseYaml, String headYaml, Predicate<String> recordChanged) {
    Map<String, Map<?, ?>> base = rows(baseYaml);
    List<String> violations = new ArrayList<>();
    rows(headYaml)
        .forEach(
            (path, head) -> {
              Map<?, ?> before = base.get(path);
              if (before == null) {
                return;
              }
              Object decision = head.get("decision");
              boolean allowed =
                  decision != null
                      && !Objects.equals(before.get("decision"), decision)
                      && recordChanged.test(decision.toString());
              if (!allowed) {
                for (String bound : List.of("max", "min")) {
                  loosening(before, head, bound)
                      .ifPresent(change -> violations.add(file + " " + path + ": " + change));
                }
              }
            });
    return violations;
  }

  public static Predicate<String> recordChangedIn(List<String> changedPaths) {
    return id ->
        changedPaths.stream().anyMatch(path -> path.startsWith("docs/decisions/" + id + "-"));
  }

  private static Optional<String> loosening(Map<?, ?> before, Map<?, ?> head, String bound) {
    if (before.get(bound) instanceof Number old && head.get(bound) instanceof Number now) {
      double step = now.doubleValue() - old.doubleValue();
      boolean loosened = "max".equals(bound) ? step > 0 : step < 0;
      if (loosened) {
        return Optional.of(
            bound
                + " went from "
                + old
                + " to "
                + now
                + " and decision: is "
                + (head.get("decision") == null ? "absent" : head.get("decision"))
                + ", not a new decision record");
      }
    }
    return Optional.empty();
  }

  private static Map<String, Map<?, ?>> rows(String yaml) {
    Map<String, Map<?, ?>> rows = new TreeMap<>();
    collect("", new Yaml().load(yaml), rows);
    return rows;
  }

  private static void collect(String path, Object node, Map<String, Map<?, ?>> rows) {
    if (!(node instanceof Map<?, ?> map)) {
      return;
    }
    if (map.containsKey("max") || map.containsKey("min")) {
      rows.put(path, map);
      return;
    }
    map.forEach(
        (key, child) -> collect(path.isEmpty() ? key.toString() : path + "." + key, child, rows));
  }
}

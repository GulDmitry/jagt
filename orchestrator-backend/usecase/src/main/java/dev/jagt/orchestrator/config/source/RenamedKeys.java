package dev.jagt.orchestrator.config.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The keys of {@code jagt.yml} that were renamed and still read as aliases, until {@link
 * #DROPPED_IN}. Paths are dotted and lie under {@code orchestrator}; the new key wins where both
 * are written.
 */
public final class RenamedKeys {

  public static final String DROPPED_IN = "0.32.0";

  private static final Pattern CAMEL = Pattern.compile("([a-z])([A-Z])");

  private static final List<Rename> ALL =
      List.of(
          new Rename("sessionHost", "multiplexer"),
          new Rename("tmuxCommand", "tmux.command"),
          new Rename("tmuxSocket", "tmux.socket"),
          new Rename("viewer.tmuxSession", "viewer.session"),
          new Rename("kittyCommand", "kitty.command"),
          new Rename("kittyFontSize", "kitty.fontSize"));

  private RenamedKeys() {}

  public record Rename(String oldKey, String newKey) {

    public String oldName() {
      return "orchestrator." + oldKey;
    }

    public String newName() {
      return "orchestrator." + newKey;
    }

    public String oldProperty() {
      return property(oldKey);
    }

    public String newProperty() {
      return property(newKey);
    }

    private static String property(String key) {
      return "orchestrator." + CAMEL.matcher(key).replaceAll("$1-$2").toLowerCase(Locale.ROOT);
    }
  }

  public static List<Rename> all() {
    return ALL;
  }

  /** The renames whose old key the section writes with a value. */
  public static List<Rename> written(@Nullable Object section) {
    List<Rename> written = new ArrayList<>();
    for (Rename rename : ALL) {
      if (valueAt(section, rename.oldKey()) != null) {
        written.add(rename);
      }
    }
    return written;
  }

  /** The section with each written old key moved to its new key, unless that is written too. */
  public static Object applied(Object section) {
    Object applied = section;
    for (Rename rename : ALL) {
      Object value = valueAt(applied, rename.oldKey());
      if (value != null && applied instanceof Map<?, ?> keys) {
        Map<Object, Object> moved = updated(keys, path(rename.oldKey()), null);
        applied =
            valueAt(moved, rename.newKey()) == null
                ? updated(moved, path(rename.newKey()), value)
                : moved;
      }
    }
    return applied;
  }

  private static List<String> path(String key) {
    return List.of(key.split("\\."));
  }

  private static @Nullable Object valueAt(@Nullable Object section, String key) {
    Object current = section;
    for (String part : path(key)) {
      current = current instanceof Map<?, ?> keys ? keys.get(part) : null;
    }
    return current;
  }

  private static Map<Object, Object> updated(
      Map<?, ?> keys, List<String> path, @Nullable Object replacement) {
    Map<Object, Object> copy = new LinkedHashMap<>(keys);
    String head = path.getFirst();
    Object replaced = replacement;
    if (path.size() > 1) {
      Map<?, ?> child = copy.get(head) instanceof Map<?, ?> found ? found : Map.of();
      replaced = updated(child, path.subList(1, path.size()), replacement);
    }
    if (replaced == null) {
      copy.remove(head);
    } else {
      copy.put(head, replaced);
    }
    return copy;
  }
}

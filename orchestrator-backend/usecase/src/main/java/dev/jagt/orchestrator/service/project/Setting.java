package dev.jagt.orchestrator.service.project;

import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One basic key of {@code jagt.yml}, named by its dotted path under {@code orchestrator}. A {@code
 * restart} key is read once at startup; any other is read again on every access.
 */
public record Setting(
    String key,
    String section,
    Kind kind,
    List<String> options,
    @Nullable String fallback,
    boolean restart) {

  public enum Kind {
    BOOLEAN,
    INTEGER,
    TEXT,
    CHOICE,
    LIST,
    NAMES
  }

  private static final Pattern DOT = Pattern.compile("\\.");

  public static Setting of(String section, String key, Kind kind) {
    return new Setting(key, section, kind, List.of(), null, false);
  }

  /** A choice; {@code fallback} is the option that applies while the file leaves the key out. */
  public static Setting choice(String section, String key, String fallback, String... options) {
    return new Setting(key, section, Kind.CHOICE, List.of(options), fallback, false);
  }

  /** The value that applies while the file leaves the key out. */
  public Setting defaulting(Object value) {
    return new Setting(key, section, kind, options, String.valueOf(value), restart);
  }

  public Setting needingRestart() {
    return new Setting(key, section, kind, options, fallback, true);
  }

  /** The key's names, outermost first. */
  List<String> names() {
    return List.of(DOT.split(key));
  }

  /** A value the board sent, checked against what this setting holds. */
  Object accepted(@Nullable Object value) {
    return switch (kind) {
      case BOOLEAN -> value instanceof Boolean ? value : refused("true or false");
      case INTEGER ->
          value instanceof Integer number && number >= 0
              ? number
              : refused("a whole number, zero or more");
      case TEXT -> value instanceof String ? value : refused("text");
      case CHOICE ->
          value instanceof String text && options.contains(text)
              ? text
              : refused("one of " + String.join(", ", options));
      case LIST ->
          value instanceof List<?> items && items.stream().allMatch(String.class::isInstance)
              ? List.copyOf(items)
              : refused("a list of text");
      case NAMES -> throw new IllegalArgumentException(key + " is a list of names shown only");
    };
  }

  private Object refused(String wanted) {
    throw new IllegalArgumentException(key + " takes " + wanted);
  }
}

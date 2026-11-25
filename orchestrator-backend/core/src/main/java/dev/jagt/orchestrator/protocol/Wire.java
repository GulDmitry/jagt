package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.task.text.Blank;
import java.util.ArrayList;
import java.util.Collection;
import org.jspecify.annotations.Nullable;

/** How a value on the wire reads: a field left blank and one left out are the same answer. */
public final class Wire {

  private Wire() {}

  public static boolean blank(@Nullable String value) {
    return value == null || value.isBlank();
  }

  public static @Nullable String absent(@Nullable String value) {
    return Blank.toNull(value);
  }

  public static boolean webLink(String value) {
    return value.startsWith("http://") || value.startsWith("https://");
  }

  /** {@code values} plus the one answer that names none of them. */
  static Collection<String> withNone(Collection<String> values, String none) {
    var allowed = new ArrayList<String>(values);
    allowed.add(none);
    return allowed;
  }
}

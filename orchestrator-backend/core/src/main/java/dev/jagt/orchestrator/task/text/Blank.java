package dev.jagt.orchestrator.task.text;

import org.jspecify.annotations.Nullable;

/** A blank text and a missing one are the same answer. */
public final class Blank {

  private Blank() {}

  public static @Nullable String toNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}

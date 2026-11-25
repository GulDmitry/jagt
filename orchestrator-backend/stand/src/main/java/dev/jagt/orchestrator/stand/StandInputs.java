package dev.jagt.orchestrator.stand;

import org.jspecify.annotations.Nullable;

public final class StandInputs {

  private StandInputs() {}

  public static String of(String key, String fallback) {
    return System.getProperty(key, fallback);
  }

  public static @Nullable String of(String key) {
    return System.getProperty(key);
  }
}

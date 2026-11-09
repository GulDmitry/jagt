package dev.jagt.orchestrator.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The fetches one sweep has already made, so its tasks on one repository share the first. */
public final class SweepFetches {

  private static final ThreadLocal<SweepFetches> CURRENT = new ThreadLocal<>();

  private final Map<String, Boolean> done = new ConcurrentHashMap<>();

  public static SweepFetches create() {
    return new SweepFetches();
  }

  public OriginContext.Scope open() {
    SweepFetches previous = CURRENT.get();
    CURRENT.set(this);
    return () -> {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    };
  }

  static boolean made(String key) {
    SweepFetches sweep = CURRENT.get();
    return sweep != null && sweep.done.containsKey(key);
  }

  static void record(String key) {
    SweepFetches sweep = CURRENT.get();
    if (sweep != null) {
      sweep.done.put(key, Boolean.TRUE);
    }
  }
}

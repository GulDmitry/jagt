package dev.jagt.orchestrator.adapter.tracker;

import org.jspecify.annotations.Nullable;

final class StageMatch {

  private StageMatch() {}

  /**
   * Spelled as the tracker reports it, case aside: anything looser starts work on an item nobody
   * meant.
   */
  static boolean same(@Nullable String read, @Nullable String configured) {
    return configured != null
        && !configured.isBlank()
        && read != null
        && read.strip().equalsIgnoreCase(configured.strip());
  }
}

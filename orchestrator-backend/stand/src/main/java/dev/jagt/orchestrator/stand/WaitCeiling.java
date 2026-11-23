package dev.jagt.orchestrator.stand;

import java.time.Duration;

/** A wait ends a hang; it never judges speed, so one limit fits a slow runner and a halved CPU. */
final class WaitCeiling {

  static final Duration LIMIT = Duration.ofMinutes(5);

  private WaitCeiling() {}
}

package dev.jagt.orchestrator.stand;

import java.time.Clock;
import java.time.Instant;

public final class StandClock {

  private static final Clock WALL = Clock.systemUTC();

  private StandClock() {}

  public static Instant now() {
    return Instant.now(WALL);
  }
}

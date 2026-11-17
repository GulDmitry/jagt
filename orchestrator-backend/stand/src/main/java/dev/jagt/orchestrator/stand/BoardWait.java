package dev.jagt.orchestrator.stand;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

final class BoardWait {

  private BoardWait() {}

  static boolean up(StandBoard board, Duration limit, Runnable between) {
    Instant deadline = Instant.now(Clock.systemUTC()).plus(limit);
    while (!board.ready()) {
      if (Instant.now(Clock.systemUTC()).isAfter(deadline)) {
        return false;
      }
      between.run();
      JagtProcess.pause(Duration.ofSeconds(1));
    }
    return true;
  }

  static boolean down(StandBoard board, Duration limit) {
    Instant deadline = Instant.now(Clock.systemUTC()).plus(limit);
    while (board.ready()) {
      if (Instant.now(Clock.systemUTC()).isAfter(deadline)) {
        return false;
      }
      JagtProcess.pause(Duration.ofMillis(200));
    }
    return true;
  }
}

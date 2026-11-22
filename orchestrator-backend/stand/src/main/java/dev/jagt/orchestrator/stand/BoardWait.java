package dev.jagt.orchestrator.stand;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

final class BoardWait {

  private BoardWait() {}

  static boolean up(StandBoard board, Duration limit, Runnable between) {
    Instant deadline = StandClock.now().plus(limit);
    while (!board.ready()) {
      if (StandClock.now().isAfter(deadline)) {
        return false;
      }
      between.run();
      JagtProcess.pause(Duration.ofSeconds(1));
    }
    return true;
  }

  static boolean until(BooleanSupplier done, Duration limit) {
    Instant deadline = StandClock.now().plus(limit);
    while (!done.getAsBoolean()) {
      if (StandClock.now().isAfter(deadline)) {
        return false;
      }
      JagtProcess.pause(Duration.ofMillis(200));
    }
    return true;
  }

  static boolean down(StandBoard board, Duration limit) {
    Instant deadline = StandClock.now().plus(limit);
    while (board.ready()) {
      if (StandClock.now().isAfter(deadline)) {
        return false;
      }
      JagtProcess.pause(Duration.ofMillis(200));
    }
    return true;
  }
}

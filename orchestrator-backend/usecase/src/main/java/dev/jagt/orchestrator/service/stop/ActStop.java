package dev.jagt.orchestrator.service.stop;

import org.jspecify.annotations.Nullable;

/** Why act reads as judge; {@code at}, epoch millis, is set where the cause has a moment. */
public record ActStop(Cause cause, @Nullable Long at) {

  public enum Cause {
    STRIKES,
    BOARD
  }
}

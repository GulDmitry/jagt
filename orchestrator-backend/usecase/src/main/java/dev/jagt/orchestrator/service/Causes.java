package dev.jagt.orchestrator.service;

import java.util.Objects;

/** A failure as a log line's cause: never null, never blank. */
public final class Causes {

  private Causes() {}

  /** Its message on one line, else its type's name. */
  public static String of(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank()
        ? failure.getClass().getSimpleName()
        : Objects.requireNonNull(OneLine.clipped(message));
  }
}

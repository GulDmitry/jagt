package dev.jagt.orchestrator.task;

import java.util.Locale;

/** A failed read has a blank class and a {@code failure}; the stop is then not read again. */
public record StopTriage(
    Who who, String classText, String family, Lift lift, String evidence, String failure) {

  public static final String UNKNOWN_FAMILY = "unknown";

  public enum Who {
    SESSION,
    MASTER,
    CASE,
    JAGT,
    NONE;

    public String label() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public enum Lift {
    SAFE,
    UNSAFE;

    public String label() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public static StopTriage failed(String cause) {
    return new StopTriage(Who.NONE, "", UNKNOWN_FAMILY, Lift.UNSAFE, "", cause);
  }

  public String line() {
    if (!failure.isBlank()) {
      return "triage could not read: " + failure;
    }
    return "triage reads: "
        + who.label()
        + " — "
        + classText
        + " ("
        + family
        + "), lift looks "
        + lift.label();
  }
}

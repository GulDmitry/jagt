package dev.jagt.orchestrator.port.codehost.review;

/** Whether the host marks a note's thread resolved; unknown where it has no such mark. */
public enum Resolution {
  OPEN,
  RESOLVED,
  UNKNOWN
}

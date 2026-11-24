package dev.jagt.orchestrator.port.codehost.review;

import java.util.Locale;

/** Whether a request still takes review: open, merged or closed unmerged. */
public enum RequestState {
  OPEN,
  MERGED,
  CLOSED;

  /** A host's word for a state; open where it names none that ends a request. */
  public static RequestState of(String hostWord) {
    return switch (hostWord.strip().toLowerCase(Locale.ROOT)) {
      case "merged" -> MERGED;
      case "closed" -> CLOSED;
      default -> OPEN;
    };
  }
}

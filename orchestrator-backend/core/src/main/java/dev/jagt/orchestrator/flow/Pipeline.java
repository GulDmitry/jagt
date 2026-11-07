package dev.jagt.orchestrator.flow;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * What the code host last said about the checks, as a verdict. Each driver and each read says it in
 * jagt's own five words, {@code success}, {@code failed}, {@code running}, {@code none}, {@code
 * unknown}; a host's own words stop at its driver.
 */
@RequiredArgsConstructor
public enum Pipeline {

  /** Failed, cancelled or timed out — nothing green will come of this run. */
  RED(0),
  /** Still going, or queued: the answer is not in yet. */
  RUNNING(1),
  /**
   * Nobody has read them, or the host said a word this does not recognise. Separate from {@link
   * #NONE}, which is a fact the HOST stated.
   */
  UNKNOWN(2),
  /**
   * The host listed no pipeline for this request. Deliberately NOT green: nothing here says the
   * code works.
   */
  NONE(3),
  GREEN(4);

  /** Worst first, for merging several repositories' rounds into one. */
  private final int severity;

  public int severity() {
    return severity;
  }

  public static Pipeline of(@Nullable String said) {
    if (said == null || said.isBlank()) {
      return UNKNOWN;
    }
    String word = said.toLowerCase(Locale.ROOT);
    if (word.contains("fail")) {
      return RED;
    }
    // `completed` is deliberately absent: it says only that a run FINISHED, never that it passed.
    if (word.contains("success")) {
      return GREEN;
    }
    if (word.contains("running")) {
      return RUNNING;
    }
    return word.contains("none") ? NONE : UNKNOWN;
  }
}

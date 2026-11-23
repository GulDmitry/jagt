package dev.jagt.gradle.suitestats;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/** A suite past its ceiling still finishes; the overrun is one line, never a failure. */
public final class Overrun {

  private Overrun() {}

  public static Optional<String> line(
      String suite, long seconds, long ceilingMinutes, double load, String power) {
    if (seconds <= ceilingMinutes * 60) {
      return Optional.empty();
    }
    return Optional.of(
        String.format(
            Locale.ROOT,
            "WARN suite.overrun suite=%s minutes=%d ceiling=%d load=%.1f power=%s",
            suite,
            (seconds + 59) / 60,
            ceilingMinutes,
            load,
            power));
  }

  /** Every task with a ceiling is recorded, a gate task such as an eval included. */
  public static boolean tracked(
      String path, Collection<String> timed, Collection<String> ceilings) {
    return timed.contains(path) || ceilings.contains(path);
  }

  /** The power source a `pmset -g batt` report names. */
  public static String power(String report) {
    if (report.contains("'AC Power'")) {
      return "ac";
    }
    return report.contains("'Battery Power'") ? "battery" : "unknown";
  }
}

package dev.jagt.orchestrator.service.master;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the Master says to a stop: lift it, send the session back to work with an instruction, or
 * leave it to the human.
 */
public record MasterStopAnswer(Kind kind, String text) {

  public enum Kind {
    LIFT,
    RETURN,
    HUMAN
  }

  private static final Pattern VERB =
      Pattern.compile("(lift|return|human):\\s*(\\S.*)", Pattern.CASE_INSENSITIVE);

  /** The first line decides; a verb with no words after it, or any other line, is the human's. */
  public static MasterStopAnswer of(String answer) {
    String line = answer.strip().lines().findFirst().orElse("").strip();
    Matcher said = VERB.matcher(line);
    return said.matches()
        ? new MasterStopAnswer(Kind.valueOf(said.group(1).toUpperCase(Locale.ROOT)), said.group(2))
        : new MasterStopAnswer(Kind.HUMAN, line);
  }
}

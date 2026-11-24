package dev.jagt.orchestrator.task.review;

import org.jspecify.annotations.Nullable;

/** The line a note written by a model opens with; a note opening with it awaits an answer. */
public final class AiLine {

  public static final String TEXT = "AI note: 👍 helps, 👎 does not; reply with what is wrong.";

  private static final String EARLIER =
      "AI review note: react with a thumbs up if it helps, a thumbs down if it does not.";

  private AiLine() {}

  public static boolean opens(@Nullable String body) {
    if (body == null) {
      return false;
    }
    String stripped = body.stripLeading();
    return stripped.startsWith(TEXT) || stripped.startsWith(EARLIER);
  }
}

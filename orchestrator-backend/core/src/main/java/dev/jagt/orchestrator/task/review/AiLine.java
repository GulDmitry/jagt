package dev.jagt.orchestrator.task.review;

import org.jspecify.annotations.Nullable;

/** The line a note written by a model opens with; a note opening with it awaits an answer. */
public final class AiLine {

  public static final String TEXT =
      "AI review note: react with a thumbs up if it helps, a thumbs down if it does not.";

  private AiLine() {}

  public static boolean opens(@Nullable String body) {
    return body != null && body.stripLeading().startsWith(TEXT);
  }
}

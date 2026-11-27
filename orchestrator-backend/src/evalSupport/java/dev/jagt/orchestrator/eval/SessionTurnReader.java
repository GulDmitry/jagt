package dev.jagt.orchestrator.eval;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells a session the brief's standing rules and one step, what its commands showed, and reads the
 * status report it ends the turn with.
 */
public record SessionTurnReader(Path brief, String model) {

  private static final Pattern HEADING = Pattern.compile("(?m)^## ([a-z][a-z0-9-]*)$");

  private static final String SHAPE =
      """
      # Output shape for this run
      You run no tool in this run: what each command you would run showed is above.
      Answer with one JSON object and nothing else, the update_agent_status call ending this turn:
      {"status": "IN_PROGRESS" | "PLAN_PENDING" | "REVIEW_PENDING" | "CI_POLLING",
      "outcome": "plain" | "question" | "permission" | "no_changes" | "wait",
      "message": "<10 words at most>", "reviewRequestUrl": "<the url, or empty>"}
      """;

  /** The status and outcome reported, each as the session wrote it. */
  public record Reported(String status, String outcome) {}

  public Reported read(SessionTurnCase turn) throws IOException, InterruptedException {
    String text = Files.readString(brief, UTF_8);
    String answer =
        new ToollessCli(model)
            .answer(
                "# Your standing rules\n"
                    + section(text, "context")
                    + "\n# Your launch instruction\n"
                    + section(text, "start")
                    + "\n# task_context.md\n"
                    + turn.facts()
                    + "\n"
                    + section(text, turn.step())
                    + turn.carried()
                    + "\n# What your commands showed\n"
                    + turn.situation()
                    + "\n"
                    + SHAPE);
    int open = answer.indexOf('{');
    int close = answer.lastIndexOf('}');
    if (open < 0 || close < open) {
      throw EvalStalled.because("no JSON object in the answer: " + answer);
    }
    Map<String, Object> root = Yamls.load(answer.substring(open, close + 1));
    return new Reported(Yamls.text(root.get("status")), Yamls.text(root.get("outcome")));
  }

  private static String section(String brief, String step) {
    Matcher heading = HEADING.matcher(brief);
    while (heading.find()) {
      if (heading.group(1).equals(step)) {
        int start = heading.end();
        return brief.substring(start, heading.find() ? heading.start() : brief.length());
      }
    }
    throw new IllegalArgumentException("the brief holds no ## " + step + " section");
  }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.text.Outside;
import dev.jagt.orchestrator.task.turn.Step;
import java.util.ArrayList;
import java.util.List;

final class RoundBrief {

  private RoundBrief() {}

  /**
   * The brief's round sections this round's checks and threads call for, in the order they are
   * told.
   */
  static List<Step> steps(ReviewFacts r, String said) {
    List<Step> steps = new ArrayList<>();
    if (Pipeline.of(said) == Pipeline.RED) {
      steps.add(Step.ROUND_CHECKS);
    }
    if (!r.discussion().threads().isEmpty()) {
      steps.add(Step.ROUND_THREADS);
    }
    // An unanswered question ENDS the round rather than parking in it: staying CI_POLLING would
    // have the poll
    // re-brief the agent on the very comments it was told to hold.
    steps.add(
        r.discussion().threads().isEmpty() ? Step.ROUND_CHECKS_DONE : Step.ROUND_THREADS_DONE);
    return List.copyOf(steps);
  }

  /**
   * The round's facts among the brief's round sections; {@code since} tells its threads from the
   * last round's.
   */
  static String of(
      String mrUrl,
      ReviewFacts r,
      String said,
      String since,
      List<String> authors,
      SessionBrief sessionBrief) {
    List<Step> steps = steps(r, said);
    StringBuilder brief = new StringBuilder("Review round for ").append(mrUrl).append(".\n");
    if (steps.contains(Step.ROUND_CHECKS)) {
      brief.append("Checks: ").append(said).append(" — find out why and fix it.\n");
      if (!r.pipeline().failure().isBlank()) {
        brief.append(Outside.fence("checks", r.pipeline().failure())).append('\n');
      }
      brief.append(section(sessionBrief, Step.ROUND_CHECKS));
    }
    if (steps.contains(Step.ROUND_THREADS)) {
      brief
          .append(section(sessionBrief, Step.ROUND_THREADS))
          .append(since.isBlank() ? "" : since + "\n")
          .append(Outside.fence("threads", String.join("\n\n", r.discussion().threads())))
          .append('\n');
      if (!authors.isEmpty()) {
        brief
            .append("Author lines, one per thread in the order above; copy each, verbatim,")
            .append(" under the heading of the block that answers its thread:\n");
        authors.forEach(line -> brief.append(line).append('\n'));
      }
    }
    brief.append(section(sessionBrief, steps.getLast()));
    return brief.toString().stripTrailing();
  }

  /** The brief asking the session to name {@code tag}; unchanged where there is none. */
  static String named(String brief, String tag) {
    return tag.isBlank() ? brief : brief + "\nName `round " + tag + "` in your report's message.";
  }

  private static String section(SessionBrief sessionBrief, Step step) {
    return sessionBrief
        .section(step)
        .filter(text -> !text.isBlank())
        .map(text -> text + "\n")
        .orElse("");
  }
}

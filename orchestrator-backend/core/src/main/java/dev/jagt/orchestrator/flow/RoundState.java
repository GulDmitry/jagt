package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Map;
import lombok.With;

/**
 * What the last review round left behind: what the agent said about it, whether replies it drafted
 * are still waiting, whether the Master has yet to read it, and where the Master still stands in
 * for the human on it. All decide the human's next move, and none is in {@link TaskStatus}. {@code
 * requestless} is that the work stands pushed where no request is opened. {@code repliesWrittenAt}
 * is when the drafted replies were last written, 0 where none stand.
 */
public record RoundState(
    AgentReport report,
    boolean draftedReplies,
    @With boolean masterReading,
    @With Arbiter.Profile master,
    @With Map<String, CheckerPhase> checkers,
    @With boolean requestless,
    @With long repliesWrittenAt) {

  public static final RoundState NONE = of(AgentReport.PLAIN, false);

  public static RoundState of(AgentReport report, boolean draftedReplies) {
    return new RoundState(report, draftedReplies, false, Arbiter.Profile.OFF, Map.of(), false, 0);
  }
}

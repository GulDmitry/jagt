package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.flow.session.SessionStanding;
import dev.jagt.orchestrator.service.review.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.turn.Step;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A round with work for the agent, relayed unless it is the last one again, the agent's question
 * stands, the task stands stopped, or no case sends one of its steps at the task's status.
 */
@Service
@RequiredArgsConstructor
public class RoundRelay {

  private final TaskStops stops;
  private final StepRelay steps;
  private final CaseDoor door;
  private final SessionBrief sessionBrief;
  private final ThreadAuthors authors;

  public SweepResult relay(
      String taskId, RoundSource source, String mrUrl, ReviewFacts round, String said, String tip) {
    ReviewFacts r = stops.asking(taskId) ? round.withoutChecks() : round;
    SessionStanding standing = steps.standing(taskId);
    String told = RoundRecord.orUnknown(r.pipeline().status());
    String read = "sweep " + taskId + ": " + r.discussion().threads().size() + " thread(s)";
    TaskStatus checked = null;
    for (Step step : RoundBrief.steps(r, told)) {
      CaseDoor.Admission admission = door.opens(taskId, step);
      if (!admission.admitted()) {
        return new SweepResult(
            SweepResult.Kind.REFUSED,
            read
                + ", checks "
                + said
                + " — no case sends this round at the task's status, so nothing was relayed; see"
                + " misses.jsonl");
      }
      checked = admission.status();
    }
    String brief =
        RoundBrief.of(
            mrUrl,
            r,
            told,
            stops.sinceLast(taskId, r),
            authors.lines(taskId, source, mrUrl, r.discussion().threads()),
            sessionBrief);
    return switch (stops.round(taskId, r, brief, tip)) {
      case STOPPED -> stopped(read, said);
      case UNCHANGED -> unchanged(read, said);
      case ASKING -> asking(read, said);
      case RELAY ->
          steps.relayComposed(taskId, RoundBrief.steps(r, told), brief, checked, standing)
              ? new SweepResult(SweepResult.Kind.RELAYED, read + " relayed, checks " + said)
              : stops.stopped(taskId) ? stopped(read, said) : unchanged(read, said);
    };
  }

  private static SweepResult stopped(String read, String said) {
    return new SweepResult(
        SweepResult.Kind.STOPPED,
        read
            + ", checks "
            + said
            + " — jagt stopped this task, so nothing was relayed; `lift` it first");
  }

  private static SweepResult asking(String read, String said) {
    return new SweepResult(
        SweepResult.Kind.PENDING,
        read
            + ", checks "
            + said
            + " — the agent asked and waits for the human, so nothing was relayed");
  }

  private static SweepResult unchanged(String read, String said) {
    return new SweepResult(
        SweepResult.Kind.UNCHANGED,
        read
            + ", checks "
            + said
            + " — unchanged since the last relay, so the agent was left alone");
  }
}

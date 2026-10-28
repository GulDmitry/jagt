package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.service.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A round with work for the agent, relayed unless it is the last one again, the agent's question stands, the task stands stopped, or no case
 * sends one of its steps at the task's status.
 */
@Service
@RequiredArgsConstructor
public class RoundRelay {

    private final TaskStops stops;
    private final StepRelay steps;
    private final CaseDoor door;
    private final SessionBrief sessionBrief;

    public SweepResult relay(String taskId, String mrUrl, ReviewFacts r, String said, String tip) {
        String read = "sweep " + taskId + ": " + r.threads().size() + " thread(s)";
        TaskStatus checked = null;
        for (String step : RoundBrief.steps(r, said)) {
            CaseDoor.Admission admission = door.opens(taskId, step);
            if (!admission.admitted()) {
                return new SweepResult(SweepResult.Kind.REFUSED, read + ", checks " + said
                        + " — no case sends this round at the task's status, so nothing was relayed; see misses.jsonl");
            }
            checked = admission.status();
        }
        String brief = RoundBrief.of(mrUrl, r, said, sessionBrief);
        return switch (stops.round(taskId, r, brief, tip)) {
            case STOPPED -> stopped(read, said);
            case UNCHANGED -> unchanged(read, said);
            case ASKING -> asking(read, said);
            case RELAY -> steps.relayComposed(taskId, RoundBrief.steps(r, said), brief, checked)
                    ? new SweepResult(SweepResult.Kind.RELAYED, read + " relayed, checks " + said)
                    : stops.stopped(taskId) ? stopped(read, said) : unchanged(read, said);
        };
    }

    private static SweepResult stopped(String read, String said) {
        return new SweepResult(SweepResult.Kind.STOPPED, read + ", checks " + said
                + " — jagt stopped this task, so nothing was relayed; `lift` it first");
    }

    private static SweepResult asking(String read, String said) {
        return new SweepResult(SweepResult.Kind.PENDING, read + ", checks " + said
                + " — the agent asked and waits for the human, so nothing was relayed");
    }

    private static SweepResult unchanged(String read, String said) {
        return new SweepResult(SweepResult.Kind.UNCHANGED, read + ", checks " + said
                + " — unchanged since the last relay, so the agent was left alone");
    }
}

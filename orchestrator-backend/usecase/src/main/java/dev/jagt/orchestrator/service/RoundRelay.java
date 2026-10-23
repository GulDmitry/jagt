package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.service.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.task.ReviewFacts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** A round with work for the agent, relayed unless it is the last one again or the task stands stopped. */
@Service
@RequiredArgsConstructor
public class RoundRelay {

    private final LoopGuard loops;
    private final AgentSessions sessions;
    private final SessionBrief sessionBrief;

    public SweepResult relay(String taskId, String mrUrl, ReviewFacts r, String said) {
        String brief = RoundBrief.of(mrUrl, r, said, sessionBrief);
        String read = "sweep " + taskId + ": " + r.threads().size() + " thread(s)";
        return switch (loops.round(taskId, r, brief)) {
            case STOPPED -> new SweepResult(SweepResult.Kind.STOPPED, read + ", checks " + said
                    + " — jagt stopped this task as a suspected loop, so nothing was relayed; `lift` it first");
            case UNCHANGED -> unchanged(read, said);
            case RELAY -> sessions.relayFreshIfChanged(taskId, brief)
                    ? new SweepResult(SweepResult.Kind.RELAYED, read + " relayed, checks " + said)
                    : unchanged(read, said);
        };
    }

    private static SweepResult unchanged(String read, String said) {
        return new SweepResult(SweepResult.Kind.UNCHANGED, read + ", checks " + said
                + " — unchanged since the last relay, so the agent was left alone");
    }
}

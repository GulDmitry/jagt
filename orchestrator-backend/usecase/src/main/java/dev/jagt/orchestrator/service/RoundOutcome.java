package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.service.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** What one round read means: the status it reads to, or the brief relayed to the agent. */
@Service
@RequiredArgsConstructor
public class RoundOutcome {

    private final RoundRecord roundRecord;
    private final AgentStatusReports statusReports;
    private final RoundRelay relay;

    public SweepResult settle(String taskId, String mrUrl, ReviewFacts r, String tip) {
        // THIS round's own read is what decides and what is relayed; the word the CARD carries is the last one
        // a round managed to read, and a green nobody looked at must not mark the task REVIEWED.
        String said = RoundRecord.orUnknown(r.pipelineStatus());
        RoundRecord.Recorded recorded = roundRecord.record(taskId, r);
        if (recorded.stop().isPresent()) {
            return stopped(taskId, said, recorded.stop().get());
        }
        RedCauses.Verdict red = recorded.red();
        Optional<TaskStatus> read = FlowRules.readReview(!r.threads().isEmpty() || !r.awaitingReviewer().isEmpty(),
                r.approved(), Pipeline.of(said), red.cause(), red.retryLeft());
        read.ifPresent(status -> statusReports.markRead(taskId, status));
        if (red.stop().isPresent()) {
            return stopped(taskId, said, red.stop().get());
        }
        if (read.filter(FlowRules::approved).isPresent()) {
            return new SweepResult(SweepResult.Kind.APPROVED,
                    "sweep " + taskId + ": approved, checks " + said + " — `deploy` or `done`");
        }
        if (read.filter(FlowRules::reviewed).isPresent()) {
            return new SweepResult(SweepResult.Kind.REVIEWED,
                    "sweep " + taskId + ": checks " + said
                            + ", nothing unresolved — waiting for an approval; `deploy` without one");
        }
        return open(taskId, mrUrl, red.retryLeft() ? r.withoutChecks() : r, said, tip, read.isEmpty(), red.waiting());
    }

    private SweepResult open(String taskId, String mrUrl, ReviewFacts r, String said, String tip, boolean held,
                             String waiting) {
        if (held && r.threads().isEmpty() && !r.awaitingReviewer().isEmpty()) {
            return new SweepResult(SweepResult.Kind.PENDING,
                    "sweep " + taskId + ": checks " + said + ", " + r.awaitingReviewer().size()
                            + " thread(s) answered by the agent — waiting on the reviewer");
        }
        if (held && r.threads().isEmpty()) {
            return new SweepResult(SweepResult.Kind.PENDING, "sweep " + taskId + ": checks " + said
                    + (waiting.isBlank() ? ", nothing unresolved yet, not approved — waiting"
                    : " on infrastructure, nothing unresolved — retry armed (" + waiting + ")"));
        }
        return relay.relay(taskId, mrUrl, r, said, tip);
    }

    private static SweepResult stopped(String taskId, String said, TaskStop stop) {
        return new SweepResult(SweepResult.Kind.STOPPED, "sweep " + taskId + ": checks " + said
                + " — jagt stopped this task: " + stop.reading());
    }
}

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

    public SweepResult settle(String taskId, String mrUrl, ReviewFacts r) {
        // THIS round's own read is what decides and what is relayed; the word the CARD carries is the last one
        // a round managed to read, and a green nobody looked at must not mark the task REVIEWED.
        String said = RoundRecord.orUnknown(r.pipelineStatus());
        Optional<TaskStop> stopped = roundRecord.record(taskId, r);
        if (stopped.isPresent()) {
            return new SweepResult(SweepResult.Kind.STOPPED, "sweep " + taskId + ": checks " + said
                    + " — jagt stopped this task: " + stopped.get().reading());
        }
        Optional<TaskStatus> read = FlowRules.readReview(!r.threads().isEmpty() || !r.awaitingReviewer().isEmpty(),
                r.approved(), Pipeline.of(said));
        read.ifPresent(status -> statusReports.markRead(taskId, status));
        if (read.filter(FlowRules::approved).isPresent()) {
            return new SweepResult(SweepResult.Kind.APPROVED,
                    "sweep " + taskId + ": approved, checks " + said + " — `deploy` or `done`");
        }
        if (read.filter(FlowRules::reviewed).isPresent()) {
            return new SweepResult(SweepResult.Kind.REVIEWED,
                    "sweep " + taskId + ": checks " + said
                            + ", nothing unresolved — waiting for an approval; `deploy` without one");
        }
        if (read.isEmpty() && r.threads().isEmpty() && !r.awaitingReviewer().isEmpty()) {
            return new SweepResult(SweepResult.Kind.PENDING,
                    "sweep " + taskId + ": checks " + said + ", " + r.awaitingReviewer().size()
                            + " thread(s) answered by the agent — waiting on the reviewer");
        }
        if (read.isEmpty() && r.threads().isEmpty()) {
            return new SweepResult(SweepResult.Kind.PENDING,
                    "sweep " + taskId + ": checks " + said
                            + ", nothing unresolved yet, not approved — waiting");
        }
        return relay.relay(taskId, mrUrl, r, said);
    }
}

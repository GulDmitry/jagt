package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.config.MasterMove;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TaskReducer;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** The one way a plan is approved, whoever read it: the gate opens and the session is told to start. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanApproval {

    private final StateService stateService;
    private final StepRelay relay;
    private final CaseDoor door;
    private final PlanBaseline baseline;
    private final ApprovedRepos repos;

    /** Whether the session was told anything new; the gate is open either way, unless no case allowed the approval. */
    public boolean approve(String taskId, String reader) {
        return approve(taskId, reader, null);
    }

    /** The same under a claim, which opens the gate in its own write, so a task that moved on keeps its gate. */
    public boolean approve(String taskId, String reader, StepRelay.@Nullable Claim claim) {
        String id = stateService.canonicalTaskId(taskId);
        if (claim != null) {
            relay.requireStanding(id, Step.PLAN, claim);
        }
        if (!door.presses(id, MasterMove.APPROVE_PLAN.id())) {
            return false;
        }
        boolean placed = repos.place(id);
        baseline.keep(id);
        Standing read = stateService.task(id).map(Standing::of).orElse(Standing.ABSENT);
        if (claim == null) {
            stateService.updateTask(id, TaskReducer::planApproved);
        }
        String facts = reader + ": the plan holds.";
        boolean told = claim == null ? tell(id, facts, placed, read) : tell(id, facts, placed, opened(claim));
        if (claim != null && !told) {
            stateService.updateTask(id, TaskReducer::planApproved);
        }
        repos.leave(id);
        log.atInfo().setMessage("plan approved").addKeyValue("task", id).addKeyValue("reader", reader).log();
        return told;
    }

    /** A task just placed starts a fresh session in its new primary: its plan carries the context. */
    private boolean tell(String id, String facts, boolean fresh, Standing read) {
        return fresh ? relay.relayUnder(id, Step.PLAN, facts, true, read) : relay.relay(id, Step.PLAN, facts, read);
    }

    private boolean tell(String id, String facts, boolean fresh, StepRelay.Claim claim) {
        return fresh ? relay.relayUnder(id, Step.PLAN, facts, true, claim) : relay.relay(id, Step.PLAN, facts, claim);
    }

    private static StepRelay.Claim opened(StepRelay.Claim claim) {
        return new StepRelay.Claim(claim.read(),
                opens -> claim.write().apply(task -> TaskReducer.planApproved(opens.apply(task))), claim.undo());
    }
}

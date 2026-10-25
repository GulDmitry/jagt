package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.MasterMove;
import dev.jagt.orchestrator.task.PlanGate;
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

    /** Whether the session was told anything new; the gate is open either way, unless no case allowed the approval. */
    public boolean approve(String taskId, String reader) {
        return approve(taskId, reader, null);
    }

    /** The same under a claim, which opens the gate in its own write, so a task that moved on keeps its gate. */
    public boolean approve(String taskId, String reader, StepRelay.@Nullable Claim claim) {
        String id = stateService.canonicalTaskId(taskId);
        if (claim != null) {
            relay.requireStanding(id, "plan", claim);
        }
        if (!door.presses(id, MasterMove.APPROVE_PLAN.id())) {
            return false;
        }
        if (claim == null) {
            stateService.updateTask(id, task -> task.withPlanGate(PlanGate.OPEN));
        }
        boolean told = claim == null ? relay.relay(id, "plan", reader + ": the plan holds.")
                : relay.relay(id, "plan", reader + ": the plan holds.", opened(claim));
        if (claim != null && !told) {
            stateService.updateTask(id, task -> task.withPlanGate(PlanGate.OPEN));
        }
        log.atInfo().setMessage("plan approved").addKeyValue("task", id).addKeyValue("reader", reader).log();
        return told;
    }

    private static StepRelay.Claim opened(StepRelay.Claim claim) {
        return new StepRelay.Claim(claim.read(), claim.standing(),
                opens -> claim.write().apply(task -> opens.apply(task).withPlanGate(PlanGate.OPEN)), claim.undo());
    }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.PlanGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** The one way a plan is approved, whoever read it: the gate opens and the session is told to start. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanApproval {

    private final StateService stateService;
    private final StepRelay relay;

    /** Whether the session was told anything new; the gate is open either way. */
    public boolean approve(String taskId, String reader) {
        String id = stateService.canonicalTaskId(taskId);
        stateService.updateTask(id, task -> task.withPlanGate(PlanGate.OPEN));
        log.atInfo().setMessage("plan approved").addKeyValue("task", id).addKeyValue("reader", reader).log();
        return relay.relay(id, "plan", reader + ": the plan holds.");
    }
}

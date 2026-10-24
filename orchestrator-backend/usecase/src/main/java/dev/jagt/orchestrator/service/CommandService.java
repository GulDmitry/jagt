package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.ActionOrigin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What every surface calls to act on one task. It adds no rules of its own: the gate, the doer and the transition
 * all live in {@link FlowEngine}, where the Master may press in {@link CaseDoor}.
 */
@Service
@RequiredArgsConstructor
public class CommandService {

    private final FlowEngine flow;
    private final CaseDoor door;

    /** Runs {@code action} on {@code taskIdOrAlias} and returns the sentence to show the human. */
    public String execute(String taskIdOrAlias, TaskAction action) {
        if (OriginContext.current() == ActionOrigin.MASTER) {
            if (action.humanOnly()) {
                throw new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE,
                        action.label() + " is a human's to press, never the Master's.");
            }
            if (!door.presses(taskIdOrAlias, action.id())) {
                return action.id() + " " + taskIdOrAlias + ": no master case allows it at this status; see misses.jsonl";
            }
        }
        return flow.run(taskIdOrAlias, action);
    }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.ActionOrigin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What every surface calls to act on one task. It adds no rules of its own: the gate, the doer and the transition
 * all live in {@link FlowEngine}.
 */
@Service
@RequiredArgsConstructor
public class CommandService {

    private final FlowEngine flow;

    /** Runs {@code action} on {@code taskIdOrAlias} and returns the sentence to show the human. */
    public String execute(String taskIdOrAlias, TaskAction action) {
        if (action.humanOnly() && OriginContext.current() == ActionOrigin.MASTER) {
            throw new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE,
                    action.label() + " is a human's to press, never the Master's.");
        }
        return flow.run(taskIdOrAlias, action);
    }
}

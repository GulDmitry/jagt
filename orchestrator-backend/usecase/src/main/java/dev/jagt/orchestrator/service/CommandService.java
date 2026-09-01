package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.TaskAction;
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
        return flow.run(taskIdOrAlias, action);
    }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.model.TaskAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What every surface calls to act on one task: a typed command in the console, a button on the board, an HTTP
 * POST, a Master-only MCP tool. It adds no rules of its own — the gate, the doer and the transition all live in
 * {@link FlowEngine} — so "what does ship do" has one answer however it was asked for.
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

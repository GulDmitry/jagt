package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.TaskStatus;

import java.util.Map;

/**
 * A status message that passed, in the types the rest of jagt works in. Nothing downstream re-reads the wire:
 * which status, what the round CLAIMS, which request it is about and what the human is to be shown are resolved
 * here, once. {@code claimed} is a claim rather than a verdict — measuring it needs a worktree, which no message
 * carries.
 */
public record Reported(TaskStatus status, AgentReport claimed, String detail, String link,
                       Map<String, String> requests) {

    public Reported {
        requests = requests == null ? Map.of() : Map.copyOf(requests);
    }
}

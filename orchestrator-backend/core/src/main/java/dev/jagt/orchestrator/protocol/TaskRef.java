package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** A tool that does one thing to one task and needs nothing else said. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskRef(String taskId) implements Message {

    public TaskRef {
        taskId = taskId == null || taskId.isBlank() ? null : taskId;
    }

    /** Each tool describes itself; the field is the same field everywhere. */
    public static Schema schema(String description) {
        return Schema.of(description).required("taskId", "string", "Task id or its short alias (p1, s2, …).");
    }

    @Override
    public List<Violation> violations(MessageContext context) {
        return taskId == null ? List.of(new Violation("taskId", "required: the task to act on")) : List.of();
    }
}

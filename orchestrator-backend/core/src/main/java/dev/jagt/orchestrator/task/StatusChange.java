package dev.jagt.orchestrator.task;

import dev.jagt.orchestrator.flow.TaskStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One step a task actually took, in the order it happened. {@code at} is epoch millis; {@code origin} is null for a
 * step recorded before origins existed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusChange(TaskStatus status, long at, ActionOrigin origin) {

    public StatusChange by(ActionOrigin origin) {
        return new StatusChange(status, at, origin);
    }
}

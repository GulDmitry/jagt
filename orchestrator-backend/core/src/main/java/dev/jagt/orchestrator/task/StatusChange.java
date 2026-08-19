package dev.jagt.orchestrator.task;

import dev.jagt.orchestrator.flow.TaskStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One step a task actually took, in the order it happened: it
 * kept the CURRENT status and one activity stamp, so "which steps has this been through" and "how long did it
 * sit in review" were unanswerable.
 *
 * @param status what it moved TO
 * @param at     when the move happened (epoch millis)
 * @param origin who asked for it; null for a step recorded before origins existed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusChange(TaskStatus status, long at, ActionOrigin origin) {

    public StatusChange by(ActionOrigin origin) {
        return new StatusChange(status, at, origin);
    }
}

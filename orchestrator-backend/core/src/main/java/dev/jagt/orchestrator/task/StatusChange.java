package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

/**
 * One step a task actually took, in the order it happened. {@code at} is epoch millis; {@code origin} is null for a
 * step recorded before origins existed, {@code why} wherever the asker gave no reason.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusChange(TaskStatus status, long at, @Nullable ActionOrigin origin, @Nullable String why) {

    public StatusChange(TaskStatus status, long at, @Nullable ActionOrigin origin) {
        this(status, at, origin, null);
    }

    public StatusChange by(@Nullable ActionOrigin origin) {
        return new StatusChange(status, at, origin, why);
    }

    public StatusChange because(@Nullable String why) {
        return new StatusChange(status, at, origin, why);
    }
}

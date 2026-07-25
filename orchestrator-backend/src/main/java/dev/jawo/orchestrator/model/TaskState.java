package dev.jawo.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskState(
        String project,
        String worktreePath,
        TaskStatus status,
        long lastActiveTimestamp,
        String message,
        String alias,
        String remoteUrl
) {

    public TaskState withStatus(TaskStatus status, String message) {
        return new TaskState(project, worktreePath, status, System.currentTimeMillis(), message, alias, remoteUrl);
    }

    public TaskState touched() {
        return withStatus(status, message);
    }
}

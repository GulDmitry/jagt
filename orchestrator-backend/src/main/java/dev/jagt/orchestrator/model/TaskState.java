package dev.jagt.orchestrator.model;

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
        String remoteUrl,
        String title,
        String mrUrl
) {

    public TaskState withStatus(TaskStatus status, String message) {
        return new TaskState(project, worktreePath, status, System.currentTimeMillis(), message, alias,
                remoteUrl, title, mrUrl);
    }

    public TaskState touched() {
        return withStatus(status, message);
    }

    public TaskState withMrUrl(String mrUrl) {
        return new TaskState(project, worktreePath, status, lastActiveTimestamp, message, alias,
                remoteUrl, title, mrUrl);
    }
}

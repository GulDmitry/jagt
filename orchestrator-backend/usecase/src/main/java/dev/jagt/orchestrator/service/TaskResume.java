package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.TaskName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Re-enters a task on its EXISTING branch with an already-open review request, at CI_POLLING. The request is the
 * ONLY input: its SOURCE branch is the task and its TARGET the base the next ship must update. A ticket is not
 * accepted — when it disagrees with the source branch, `ship` pushes one branch and updates another's request.
 */
@Service
@RequiredArgsConstructor
public class TaskResume {

    private final ReviewReader reviewReader;
    private final RequestProject projects;
    private final ResumeRegistration registration;

    /** Resumes whatever {@code reviewRequestUrl} names, or answers why it cannot be resumed. */
    public Launched resume(String reviewRequestUrl) {
        var read = reviewReader.readRequest(reviewRequestUrl);
        var request = read.facts();
        // Two different answers: merging them reports a live request as missing.
        if (request.isEmpty()) {
            return Launched.refused("error: read failed: " + reviewRequestUrl + " (cause in the log) —"
                    + " nothing is known about it");
        }
        if (!request.get().exists()) {
            return Launched.refused("error: no such review request: " + reviewRequestUrl + " (the host says"
                    + " so)");
        }
        String taskId = request.get().sourceBranch();
        if (taskId == null || taskId.isBlank()) {
            return Launched.refused("error: the review request names no source branch: " + reviewRequestUrl);
        }
        String unusable = TaskName.unusableReason(taskId);
        if (unusable != null) {
            return Launched.refused("error: branch '" + taskId + "' cannot be a task name (" + unusable
                    + "). Try `do <ticket> from " + taskId + "`.");
        }
        Launched linked = link(taskId, reviewRequestUrl, request.get().title(), request.get().targetBranch());
        if (linked.created()) {
            reviewReader.charge(taskId, read.usage());   // the task exists only now
        }
        return linked;
    }

    Launched link(String taskId, String mrUrl, String title, String targetBranch) {
        if (mrUrl == null || !mrUrl.contains("http")) {
            throw new IllegalArgumentException("resume needs the request url: resume <ticket> <request-url>");
        }
        TaskName.require(taskId, "taskId");
        // An unknown project is settled before the read, not after paying for one.
        return registration.register(taskId, projects.of(mrUrl), mrUrl, title, targetBranch);
    }
}

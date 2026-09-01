package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.GitRemote;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ReviewRequestTitle;
import dev.jagt.orchestrator.task.TaskName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Re-enters a task on its EXISTING branch with an already-open review request, at CI_POLLING. The request is the
 * ONLY input: its SOURCE branch is the task and its TARGET the base the next ship must update. A ticket is not
 * accepted — when it disagrees with the source branch, `ship` pushes one branch and updates another's request.
 */
@Service
@RequiredArgsConstructor
public class TaskResume {

    private final TaskProvisioning provisioning;
    private final AgentStatusReports statusReports;
    private final ConfigService configService;
    private final GitService gitService;
    private final ReviewReader reviewReader;

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
        String result = link(taskId, reviewRequestUrl, request.get().title(), request.get().targetBranch());
        reviewReader.charge(taskId, read.usage());       // the task exists only now
        return Launched.created(taskId, result);
    }

    String link(String taskId, String mrUrl, String title, String targetBranch) {
        if (mrUrl == null || !mrUrl.contains("http")) {
            throw new IllegalArgumentException("resume needs the request url: resume <ticket> <request-url>");
        }
        TaskName.require(taskId, "taskId");
        String instructions = "Reopened for review. Your branch is resumed with its existing commits and"
                + " review request " + mrUrl + " is open — there is NOTHING to build or commit right now."
                + " Do NOT re-implement, and"
                + " do NOT call update_agent_status: the Master has already set your status (CI_POLLING). Stay"
                + " idle; only when the Master relays review comments via task_context.md do you address them.";
        provisioning.initializeTask(NewTask.builder(taskId, projectFor(mrUrl))
                .instructions(instructions).branchStrategy("resume")
                // Stored bare: the pattern already prefixed the ticket, and a later ship expands it again.
                .title(ReviewRequestTitle.stripTicketPrefix(title, taskId))
                // The open request's OWN target, the host matching source AND target.
                .baseBranch(targetBranch)
                .build());
        statusReports.report(TaskStatus.CI_POLLING, "review request: " + mrUrl, taskId);
        return "Resumed " + taskId + " on its existing branch, linked " + mrUrl
                + "; CI_POLLING — `sweep` or `deploy`.";
    }

    private String projectFor(String mrUrl) {
        for (var e : configService.load().projects().entrySet()) {
            String path = GitRemote.projectPath(gitService.remoteUrl(Path.of(e.getValue().path())));
            if (path != null && mrUrl.contains(path)) {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("no configured project matches request url: " + mrUrl);
    }
}

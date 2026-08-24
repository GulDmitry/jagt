package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.GitRemote;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ReviewRequestTitle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Re-enters a task on its EXISTING branch with an already-open review request, at CI_POLLING — so `sweep`
 * and `deploy` continue on that request instead of the next `ship` opening a second one.
 *
 * <p>The request is the ONLY input, because it carries every answer itself: its SOURCE branch is the task (a
 * jagt task IS its branch) and its TARGET is the base the next ship must update rather than open a second
 * request against. A ticket is deliberately not accepted: when it disagrees with the source branch, `ship`
 * pushes one branch and updates the request of another.
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
    public String resume(String reviewRequestUrl) {
        var read = reviewReader.readRequest(reviewRequestUrl);
        var request = read.facts();
        // Two different answers, and merging them into one is what let a live request be reported as missing.
        if (request.isEmpty()) {
            return "error: read failed: " + reviewRequestUrl + " (cause in the log) — nothing is known about it";
        }
        if (!request.get().exists()) {
            return "error: no such review request: " + reviewRequestUrl + " (the host says so)";
        }
        String taskId = request.get().sourceBranch();
        if (taskId == null || taskId.isBlank()) {
            return "error: the review request names no source branch: " + reviewRequestUrl;
        }
        // Someone else's branch is not bound by jagt's naming, and a task IS its branch — so say which branch
        // and why, instead of letting the generic id check report a regex the human never typed.
        if (!TaskProvisioning.isSafeId(taskId)) {
            return "error: branch '" + taskId + "' cannot be a task name (letters, digits, '-', '_' only;"
                    + " it becomes a directory and a tmux window too). Try `do <ticket> from " + taskId + "`.";
        }
        String result = link(taskId, reviewRequestUrl, request.get().title(), request.get().targetBranch());
        reviewReader.charge(taskId, read.usage());       // the task exists only now
        return result;
    }

    String link(String taskId, String mrUrl, String title, String targetBranch) {
        if (mrUrl == null || !mrUrl.contains("http")) {
            throw new IllegalArgumentException("resume needs the request url: resume <ticket> <request-url>");
        }
        TaskProvisioning.requireSafeId(taskId, "taskId");
        String instructions = "Reopened for review. Your branch is resumed with its existing commits and"
                + " review request " + mrUrl + " is open — there is NOTHING to build or commit right now."
                + " Do NOT re-implement, and"
                + " do NOT call update_agent_status: the Master has already set your status (CI_POLLING). Stay"
                + " idle; only when the Master relays review comments via task_context.md do you address them.";
        provisioning.initializeTask(NewTask.builder(taskId, projectFor(mrUrl))
                .instructions(instructions).branchStrategy("resume")
                // Stored bare: the pattern already prefixed the ticket, and a later ship expands it again.
                .title(ReviewRequestTitle.stripTicketPrefix(title, taskId))
                // The open request's OWN target, so the next ship updates it instead of opening a second one
                // against the project default (the host matches source AND target).
                .baseBranch(targetBranch)
                .build());
        statusReports.report("CI_POLLING", "review request: " + mrUrl, taskId);
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

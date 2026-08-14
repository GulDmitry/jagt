package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.GitRemote;
import dev.jagt.orchestrator.model.NewTask;
import dev.jagt.orchestrator.model.ReviewRequestTitle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Re-enters a task on its EXISTING branch with an already-open review request, at CI_POLLING — so `review` and
 * `deploy` continue on that request instead of the next `ship` opening a second one.
 */
@Service
@RequiredArgsConstructor
public class TaskResume {

    private final TaskProvisioning provisioning;
    private final AgentStatusReports statusReports;
    private final ConfigService configService;
    private final GitService gitService;

    public String resume(String taskId, String mrUrl, String title, String targetBranch) {
        if (mrUrl == null || !mrUrl.contains("http")) {
            throw new IllegalArgumentException("resume needs the MR url: resume <ticket> <mr-url>");
        }
        TaskProvisioning.requireSafeId(taskId, "taskId");
        String instructions = "Reopened for review. Your branch is resumed with its existing commits and MR "
                + mrUrl + " is open — there is NOTHING to build or commit right now. Do NOT re-implement, and"
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
        statusReports.report("CI_POLLING", "MR: " + mrUrl, taskId);
        return "Resumed " + taskId + " on its existing branch; linked MR " + mrUrl
                + "; status CI_POLLING — run `review` or `deploy`.";
    }

    private String projectFor(String mrUrl) {
        for (var e : configService.load().projects().entrySet()) {
            String path = GitRemote.projectPath(gitService.remoteUrl(Path.of(e.getValue().path())));
            if (path != null && mrUrl.contains(path)) {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("no configured project matches MR url: " + mrUrl);
    }
}

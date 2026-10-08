package dev.jagt.orchestrator.capability.ide;

import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.DiffCheckouts;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A task opened as a static diff against what its request targets. */
@Component
@RequiredArgsConstructor
class IdeDiff {

    private final ConfigService configService;
    private final DiffCheckouts diffCheckouts;
    private final EditorDriver editorDriver;

    /** One comparison per repository, each right side FROZEN at this call: the editor's own Refresh does nothing. */
    String open(String taskId, TaskState task) {
        List<String> against = new ArrayList<>();
        boolean many = task.repos().size() > 1;
        for (TaskRepo repo : task.repos()) {
            against.add((many ? repo.project() : "changes") + " vs " + openDiffOf(taskId, task, repo));
        }
        return "Opened STATIC diff of " + taskId + " (" + String.join(", ", against)
                + ") — snapshot, does not refresh; re-run for a fresh one";
    }

    /** The base it was read against. */
    private String openDiffOf(String taskId, TaskState task, TaskRepo repo) {
        ProjectConfig project = configService.project(repo.project());
        Path projectPath = Path.of(project.path());
        // The effective base, read exactly as the request's target is.
        String configured = task.baseBranchOr(project.baseBranch());
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Project " + repo.project() + " has no baseBranch in jagt.yml,"
                    + " so a diff of " + taskId + " has nothing to read against");
        }
        String diffBase = "origin/" + configured.replaceFirst("^origin/", "");
        Path base = diffCheckouts.checkoutBaseForDiff(projectPath, diffBase, taskId, repo.project());
        Path clean = diffCheckouts.checkoutWorktreeCleanForDiff(Path.of(repo.worktreePath()), projectPath,
                diffBase, taskId, repo.project());
        editorDriver.openDiff(base, clean);
        return diffBase;
    }
}

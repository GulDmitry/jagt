package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.Guard;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The git state a human trusted for a task, held against what git would obey now. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrustedGitState {

    private static final int NAMED = 3;

    private final StateService stateService;
    private final GitWorktrees git;

    /** Trusts what git obeys now; throws where it cannot be read. */
    public void trust(String taskId) {
        TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
        Map<String, String> now = read(task);
        stateService.updateTask(taskId, current -> current.withGitTrust(GitTrust.of(now)));
    }

    private Map<String, String> read(TaskState task) {
        return git.gitState(task.repos().stream().map(TaskRepo::worktreePath)
                .filter(path -> path != null && !path.isBlank()).map(Path::of).toList());
    }

    /**
     * Why git must not run for the task yet, empty where it obeys what was trusted. A task with nothing recorded
     * trusts what it finds; a change found stays on the task until {@link #trust} adopts it.
     */
    public Optional<String> distrust(String taskId) {
        TaskState task = stateService.task(taskId).orElse(null);
        if (task == null) {
            return Optional.empty();
        }
        GitTrust trusted = task.gitTrust();
        String parked = trusted == null ? null : trusted.changed();
        if (parked != null) {
            return Optional.of(parked);
        }
        Map<String, String> now;
        try {
            now = read(task);
        } catch (RuntimeException unreadable) {
            return Optional.of("git state of " + taskId + " could not be read: " + unreadable.getMessage());
        }
        if (trusted == null) {
            stateService.updateTask(taskId, current -> current.withGitTrust(GitTrust.of(now)));
            return Optional.empty();
        }
        List<String> differences = trusted.differences(now);
        if (differences.isEmpty()) {
            return Optional.empty();
        }
        String changed = "git config or hooks changed since trusted: "
                + String.join("; ", differences.subList(0, Math.min(NAMED, differences.size())))
                + (differences.size() > NAMED ? "; " + (differences.size() - NAMED) + " more" : "")
                + " — trust adopts them";
        stateService.updateTask(taskId, current -> current.withGitTrust(trusted.withChanged(changed)).refused(Guard.GIT_STATE));
        log.atWarn().setMessage("git state changed").addKeyValue("task", taskId)
                .addKeyValue("cause", String.join("; ", differences))
                .addKeyValue("effect", "task parked").log();
        return Optional.of(changed);
    }
}

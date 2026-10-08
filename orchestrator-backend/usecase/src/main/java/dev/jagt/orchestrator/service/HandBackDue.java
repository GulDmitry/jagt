package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Specs;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

/** Why a session may not hand its round back yet, which only the worktree can say. */
@Service
@RequiredArgsConstructor
public class HandBackDue {

    private final WorktreeChanges worktreeChanges;
    private final Specs specs;

    public Optional<String> owed(String taskId, TaskState task) {
        return notesOwed(task).or(() -> specsOwed(taskId, task));
    }

    private Optional<String> notesOwed(TaskState task) {
        Path worktree = Path.of(task.worktreePath());
        return TaskNotes.owed(worktree).or(() -> worktreeChanges.agentFileLinesAdded(task)
                .map(added -> TaskNotes.agentFileOwed(worktree, added))
                .orElse(Optional.of("jagt could not count the lines this task added to the project's agent file")));
    }

    private Optional<String> specsOwed(String taskId, TaskState task) {
        return task.repos().stream()
                .map(repo -> specs.owed(Path.of(repo.worktreePath()), taskId).map(owed -> "[" + repo.project()
                        + "] " + owed))
                .flatMap(Optional::stream).findFirst();
    }
}

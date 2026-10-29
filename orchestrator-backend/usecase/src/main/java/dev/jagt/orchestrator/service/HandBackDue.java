package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Specs;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Why a session may not hand its round back yet, which only the worktree can say. */
@Service
@RequiredArgsConstructor
@Slf4j
public class HandBackDue {

    private final WorktreeChanges worktreeChanges;
    private final Specs specs;
    private final AgentRuntime agentRuntime;

    public Optional<String> invalid(TaskState task) {
        return WorktreeFiles.read(Path.of(task.worktreePath()).resolve(Artifact.NOTES.fileName()))
                .flatMap(notes -> TaskNotes.unknownModel(notes, agentRuntime));
    }

    public Optional<String> owed(String taskId, TaskState task) {
        return notesOwed(task).or(() -> repliesOwed(task)).or(() -> specsOwed(taskId, task));
    }

    private Optional<String> repliesOwed(TaskState task) {
        return task.trailOrNone().rounds().stream().reduce((first, second) -> second)
                .filter(round -> round.threads() > 0)
                .filter(round -> task.history().stream()
                        .noneMatch(step -> step.status() == TaskStatus.SHIPPING && step.at() > round.at()))
                .filter(round -> olderThan(round.at(), Path.of(task.worktreePath()).resolve(Artifact.REPLIES.fileName())))
                .map(round -> round.threads() + " thread(s) were relayed and " + Artifact.REPLIES.fileName()
                        + " was not written since: write a block for each thread awaiting your answer, whatever the"
                        + " checks say, then hand back");
    }

    private static boolean olderThan(long relayedAt, Path file) {
        if (!Files.exists(file)) {
            return true;
        }
        try {
            return Files.getLastModifiedTime(file).toMillis() < relayedAt;
        } catch (IOException e) {
            log.atWarn().setMessage("file stat failed").addKeyValue("path", file)
                    .addKeyValue("cause", e.getMessage()).log();
            return false;
        }
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

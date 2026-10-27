package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/** Whether the notes a fresh session starts from have fallen behind the work, measured from the round's start. */
@Component
@RequiredArgsConstructor
public class NotesDue {

    private final Clock clock;

    /** The refusal text where an in-progress task's notes are older than {@code every}; empty where {@code every} is zero. */
    public Optional<String> reminder(TaskState task, Duration every) {
        if (every.isZero() || task.status() != TaskStatus.IN_PROGRESS) {
            return Optional.empty();
        }
        Path worktree = Path.of(task.worktreePath());
        long kept = Math.max(FileStamps.modified(worktree.resolve(Artifact.NOTES.fileName())),
                FileStamps.modified(worktree.resolve(Artifact.CONTEXT.fileName())));
        if (kept == 0 || clock.millis() - kept <= every.toMillis()) {
            return Optional.empty();
        }
        return Optional.of("Before you stop: rewrite " + Artifact.NOTES.fileName() + " whole, at most "
                + TaskNotes.MAX_CHARS + " characters, so a fresh session could carry on from it alone.");
    }
}

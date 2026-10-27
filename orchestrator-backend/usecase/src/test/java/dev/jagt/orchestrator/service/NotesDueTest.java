package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class NotesDueTest {

    private static final long NOW = 10_000_000_000L;
    private final NotesDue due = new NotesDue(Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));

    @Test
    void asksForNewNotesWhenTheyAreOlderThanTheIntervalAtTheTurnsEnd(@TempDir Path worktree) throws Exception {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_notes.md"), "old"),
                FileTime.fromMillis(NOW - Duration.ofMinutes(31).toMillis()));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();

        assertThat(due.reminder(task, Duration.ofMinutes(30))).hasValueSatisfying(
                reminder -> assertThat(reminder).contains("rewrite task_notes.md", "16000"));
    }

    @Test
    void asksForNotesNeverWrittenOnceTheRoundIsOlderThanTheInterval(@TempDir Path worktree) throws Exception {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_context.md"), "go"),
                FileTime.fromMillis(NOW - Duration.ofMinutes(31).toMillis()));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();

        assertThat(due.reminder(task, Duration.ofMinutes(30))).isPresent();
    }

    @Test
    void asksForNothingWhenTheNotesAreWithinTheInterval(@TempDir Path worktree) throws Exception {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_notes.md"), "fresh"),
                FileTime.fromMillis(NOW - Duration.ofMinutes(5).toMillis()));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();

        assertThat(due.reminder(task, Duration.ofMinutes(30))).isEmpty();
    }

    @Test
    void asksForNothingWhenTheIntervalIsZero(@TempDir Path worktree) throws Exception {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_notes.md"), "old"),
                FileTime.fromMillis(0));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();

        assertThat(due.reminder(task, Duration.ZERO)).isEmpty();
    }

    @Test
    void asksForNothingOfATaskThatIsNotInProgress(@TempDir Path worktree) throws Exception {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_notes.md"), "old"),
                FileTime.fromMillis(0));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();

        assertThat(due.reminder(task, Duration.ofMinutes(30))).isEmpty();
    }
}

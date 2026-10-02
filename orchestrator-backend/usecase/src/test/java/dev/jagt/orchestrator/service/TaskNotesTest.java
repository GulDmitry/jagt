package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TaskNotesTest {

    @Test
    void owesNothingWhenTheNotesWereRewrittenAfterTheRoundArrived(@TempDir Path worktree) throws Exception {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_context.md"), "round 2"),
                FileTime.from(Instant.parse("2026-01-01T10:00:00Z")));
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_notes.md"), "kept the retry: ABC-42"),
                FileTime.from(Instant.parse("2026-01-01T11:00:00Z")));

        assertThat(TaskNotes.owed(worktree)).isEmpty();
    }

    @Test
    void owesARewriteWhenTheNotesPredateTheRound(@TempDir Path worktree) throws Exception {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_notes.md"), "kept the retry"),
                FileTime.from(Instant.parse("2026-01-01T09:00:00Z")));
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_context.md"), "round 2"),
                FileTime.from(Instant.parse("2026-01-01T10:00:00Z")));

        assertThat(TaskNotes.owed(worktree))
                .contains("task_notes.md predates this round's task_context.md: rewrite it for this round");
    }

    @Test
    void owesACompressionWhenTheNotesPassTheirCap(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve("task_notes.md"), "x".repeat(16_001));

        assertThat(TaskNotes.owed(worktree))
                .contains("task_notes.md is 16001 characters, at most 16000: compress it, keeping every fact");
    }
}

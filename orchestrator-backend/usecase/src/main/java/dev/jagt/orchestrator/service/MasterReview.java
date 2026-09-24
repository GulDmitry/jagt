package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * What the Master session leaves behind about one task. A file in that task's own worktree, so it sits in the
 * artifact chain beside the diff and the drafted replies, and `ide <task>` opens all of it at once. It is the
 * ONE thing the reviewer writes: everything else in a worktree belongs to whoever works there.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterReview {

    /** In the worktree, beside `review_replies.md`, and read back by jagt rather than reported over a wire. */
    public static final String FILE = "master-review.md";

    /** The last line the reviewer writes, and the only part of the file jagt reads as an answer. */
    private static final String VERDICT = "VERDICT:";

    /** What the reviewer concluded, or empty where it has not written, or has not finished. */
    public Optional<Verdict> of(TaskState task) {
        Path file = file(task);
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Files.readAllLines(file).reversed().stream()
                    .map(String::strip)
                    .filter(line -> line.startsWith(VERDICT))
                    .findFirst()
                    .map(line -> Verdict.of(line.substring(VERDICT.length()), modified(file)));
        } catch (IOException | RuntimeException unreadable) {
            log.atWarn().setMessage("master review unreadable")
                    .addKeyValue("file", file)
                    .addKeyValue("cause", unreadable.toString())
                    .log();
            return Optional.empty();
        }
    }

    /** Whether the reviewer has looked at the task AS IT STANDS, rather than at an earlier hand-back. */
    public boolean readsTheRoundInFront(TaskState task) {
        return of(task).filter(verdict -> verdict.writtenAt() >= task.statusSince()).isPresent();
    }

    public Path file(TaskState task) {
        String worktree = task.worktreePath();
        return worktree == null || worktree.isBlank() ? null : Path.of(worktree).resolve(FILE);
    }

    /** {@code ready} is the one word that means anything; every other ending is a round going back. */
    public record Verdict(boolean ready, String said, long writtenAt) {

        static Verdict of(String line, long writtenAt) {
            String said = line.strip();
            return new Verdict(said.equalsIgnoreCase("ready"), said, writtenAt);
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException gone) {
            return 0;
        }
    }
}

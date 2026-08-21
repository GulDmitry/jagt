package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether a task is carrying answers a human still has to see. Presence alone cannot say: the file outlives the
 * round it was written in, so answers a ship already posted keep reading as answers nobody sent.
 *
 * <p>What it will NOT do is remove the file. A round stamp says a ship happened, never that the replies went out
 * — posting is relayed to the agent and off the critical path, so "posted, file not cleaned up" and "never
 * posted" are the same bytes on disk, and only one of them is safe to delete. Announcing nothing is recoverable
 * ({@code replies} prints the file whatever the card says); unlinking it is not.
 */
@Service
@RequiredArgsConstructor
public class ReviewDrafts {

    private final ConfigService configService;

    /**
     * Announced only where it is actionable, and only while a ship would still send it. Static for a caller that
     * already holds the configuration — the surfaces read it once per render, and a second load could disagree
     * with the first inside one frame.
     *
     * @param status the status the task is being MOVED to, which is not always the one it still carries
     */
    public static boolean pending(TaskState task, TaskStatus status, boolean shipPostsEveryDraft) {
        if (status != TaskStatus.REVIEW_PENDING && status != TaskStatus.CI_FAILED) {
            return false;
        }
        Path file = draftsIn(task.worktreePath());
        return file != null && Files.isRegularFile(file) && !spent(file, task, shipPostsEveryDraft);
    }

    /** The same for a caller with no configuration in hand. */
    public boolean pending(TaskState task, TaskStatus status) {
        return pending(task, status, configService.load().codeReview().shipPostsEveryDraft());
    }

    /**
     * Answered by a ship that has already happened: the drafts are older than the round now open, and that round
     * was opened by the ship that posted them. Never true where the answers are not jagt's to spend — posting
     * turned off, or an author filter under which the agent posts some and leaves the rest for the human.
     */
    public boolean spent(TaskState task) {
        Path file = draftsIn(task.worktreePath());
        return file != null && Files.isRegularFile(file) && spent(file, task, postsEveryDraft());
    }

    private boolean postsEveryDraft() {
        return configService.load().codeReview().shipPostsEveryDraft();
    }

    /** A timestamp that cannot be read leaves the file announced: whoever opens it loses nothing. */
    private static boolean spent(Path file, TaskState task, boolean shipPostsEveryDraft) {
        if (!shipPostsEveryDraft) {
            return false;
        }
        try {
            return Files.getLastModifiedTime(file).toMillis() < task.mrCreatedAt();
        } catch (IOException e) {
            return false;
        }
    }

    private static Path draftsIn(String worktree) {
        return worktree == null || worktree.isBlank()
                ? null
                : Path.of(worktree).resolve(WorktreeFiles.REVIEW_REPLIES);
    }
}

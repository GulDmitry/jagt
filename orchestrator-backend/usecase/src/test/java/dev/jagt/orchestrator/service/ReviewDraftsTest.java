package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewDraftsTest {

    /** The ship that opened the round now open is the ship that posted them. */
    @Test
    void announcesNothingForRepliesDraftedBeforeTheOpenRound(@TempDir Path worktree) throws IOException {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(1_000));

        boolean pending = ReviewDrafts.pending(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build(), TaskStatus.REVIEW_PENDING, true);

        assertThat(pending).isFalse();
    }

    @Test
    void announcesRepliesDraftedForTheRoundThatIsStillOpen(@TempDir Path worktree) throws IOException {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(3_000));

        boolean pending = ReviewDrafts.pending(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build(), TaskStatus.REVIEW_PENDING, true);

        assertThat(pending).isTrue();
    }

    /** Nobody but the human can send them, so nothing but the human ends the announcement. */
    @Test
    void keepsAnnouncingRepliesNoShipWillEverPost(@TempDir Path worktree) throws IOException {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(1_000));

        boolean pending = ReviewDrafts.pending(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build(), TaskStatus.REVIEW_PENDING, false);

        assertThat(pending).isTrue();
    }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MasterReviewTest {

    private final MasterReview reviews = new MasterReview();

    private static TaskState in(Path worktree) {
        return TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
    }

    @Test
    void readsNothingFromATaskTheReviewerHasNotWrittenAbout(@TempDir Path worktree) {
        assertThat(reviews.of(in(worktree))).isEmpty();
    }

    @Test
    void readsTheVerdictOffTheLastLineTheReviewerWrote(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE),
                "Foo.java:12 the guard is inverted\nVERDICT: not ready\n");

        assertThat(reviews.of(in(worktree))).get()
                .satisfies(verdict -> assertThat(verdict.ready()).isFalse());
    }

    @Test
    void readsOnlyTheWordThatMeansTheRoundCanGoOn(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE), "nothing wrong\nVERDICT: ready\n");

        assertThat(reviews.of(in(worktree))).get()
                .satisfies(verdict -> assertThat(verdict.ready()).isTrue());
    }

    @Test
    void ignoresAFileThatStopsBeforeTheVerdict(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE), "Foo.java:12 the guard is inverted\n");

        assertThat(reviews.of(in(worktree))).isEmpty();
    }

    @Test
    void countsAVerdictOlderThanTheRoundItSelfAsNotYetRead(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE), "VERDICT: ready\n");
        TaskState handedBackSince = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").lastActiveTimestamp(System.currentTimeMillis() + 60_000).build();

        assertThat(reviews.readsTheRoundInFront(handedBackSince)).isFalse();
    }
}

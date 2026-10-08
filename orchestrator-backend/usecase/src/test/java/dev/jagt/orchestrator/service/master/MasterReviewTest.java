package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MasterReviewTest {

    private final MasterReview reviews = new MasterReview();

    @Test
    void readsNothingFromATaskTheReviewerHasNotWrittenAbout(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.of(task)).isEmpty();
    }

    @Test
    void readsTheVerdictOffTheLastLineTheReviewerWrote(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE),
                "Foo.java:12 the guard is inverted\nVERDICT: not ready\n");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.of(task)).get()
                .satisfies(verdict -> assertThat(verdict.ready()).isFalse());
    }

    @Test
    void readsOnlyTheWordThatMeansTheRoundCanGoOn(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE), "nothing wrong\nVERDICT: ready\n");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.of(task)).get()
                .satisfies(verdict -> assertThat(verdict.ready()).isTrue());
    }

    @Test
    void countsAReadyThatStillListsFindingsAsNotReady(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE),
                "# ABC-1 review\nFoo.java:12 no test pins it\nBar.java no v2 branch remains, recorded as the cost\n"
                        + "VERDICT: ready\n");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.of(task)).get()
                .satisfies(verdict -> assertThat(verdict.kind()).isEqualTo(MasterReview.Kind.NOT_READY));
    }

    @Test
    void readsAQuestionAsTheLineAboveTheVerdict(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE),
                "Foo.java drops v2\nAdd v3 beside v2, or replace it?\n\nVERDICT: question\n");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.of(task)).get().satisfies(verdict -> {
            assertThat(verdict.kind()).isEqualTo(MasterReview.Kind.QUESTION);
            assertThat(verdict.question()).isEqualTo("Add v3 beside v2, or replace it?");
        });
    }

    @Test
    void ignoresAFileThatStopsBeforeTheVerdict(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE), "Foo.java:12 the guard is inverted\n");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.of(task)).isEmpty();
    }

    @Test
    void countsAVerdictOlderThanTheRoundItSelfAsNotYetRead(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE), "VERDICT: ready\n");
        TaskState handedBackSince = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").lastActiveTimestamp(System.currentTimeMillis() + 60_000).build();

        assertThat(reviews.readsTheRoundInFront(handedBackSince)).isFalse();
    }
}

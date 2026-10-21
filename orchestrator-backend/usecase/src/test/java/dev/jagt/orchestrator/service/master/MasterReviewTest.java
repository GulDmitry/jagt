package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

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

    @Test
    void readsAVerdictFileWhoseFirstLineIsTheVerdict(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE), "VERDICT: ready\n");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(1).build();

        assertThat(reviews.of(task)).get().satisfies(verdict -> assertThat(verdict.ready()).isTrue());
    }

    @Test
    void readsTheOwnDiffWhicheverLineCarriesIt(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE),
                "Foo.java:12 the guard is inverted\n" + MasterReview.OWN_DIFF + "abc123\nVERDICT: not ready\n");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.of(task)).get().satisfies(verdict -> assertThat(verdict.ownDiff()).isEqualTo("abc123"));
    }

    @Test
    void takesAVerdictWrittenInTheSameMomentTheRoundBeganAsAboutIt() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(5_000).build();

        boolean answers = new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), task.statusSince(), "")
                .answers(task);

        assertThat(answers).isTrue();
    }

    @Test
    void saysAVerdictInWordsWithASpaceWhereTheKindHasAnUnderscore() {
        assertThat(new MasterReview.Verdict(MasterReview.Kind.NOT_READY, List.of(), 1, "").said())
                .isEqualTo("not ready");
    }

    @Test
    void stampsTheVerdictFileAsWrittenNowAndSaysSo(@TempDir Path worktree) throws Exception {
        Path file = Files.writeString(worktree.resolve(MasterReview.FILE), "VERDICT: ready\n");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        boolean restamped = reviews.restamp(task, 5_000);

        assertThat(restamped).isTrue();
        assertThat(Files.getLastModifiedTime(file).toMillis()).isEqualTo(5_000);
    }

    @Test
    void saysNothingWasStampedWhereNoVerdictFileExists(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.restamp(task, 5_000)).isFalse();
    }
}

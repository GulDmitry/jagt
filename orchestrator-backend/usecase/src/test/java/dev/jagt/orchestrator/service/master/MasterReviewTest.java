package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void neverReadsAVerdictBackOutOfTheFileWrittenForTheHuman(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE), "nothing wrong\n\nVERDICT: ready\n");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.of(task)).isEmpty();
    }

    @Test
    void readsBackTheFindingsEveryRoleAnsweredWithTheRoleThatMadeThem(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
        Finding inverted = new Finding("Foo.java", "the guard is inverted", "inverted guard", "blocking", "check()",
                "behaviour");
        reviews.write(task, new MasterReview.Review(1, "", List.of(
                new MasterReview.Read("qa", new Judgement("", "ready", List.of(), "", List.of(), "")),
                new MasterReview.Read("developer", new Judgement("", "not ready", List.of(inverted), "", List.of(), "")))),
                "");

        var verdict = reviews.of(task).orElseThrow();

        assertThat(verdict.kind()).isEqualTo(MasterReview.Kind.NOT_READY);
        assertThat(verdict.findings()).containsExactly(new MasterReview.Said("developer", inverted));
    }

    @Test
    void writesTheFileForTheHumanBesideTheVerdict(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        reviews.write(task, new MasterReview.Review(1, "", List.of()), "VERDICT: ready\n");

        assertThat(worktree.resolve(MasterReview.FILE)).content().isEqualTo("VERDICT: ready\n");
    }

    @Test
    void doesNotTakeAVerdictAskedAboutAnEarlierHandBackForTheOneInFrontHoweverLateItWasWritten(@TempDir Path worktree) {
        TaskState handedBackAgain = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").lastActiveTimestamp(5_000).build();
        reviews.write(handedBackAgain, new MasterReview.Review(1_000, "", List.of()), "");

        assertThat(reviews.readsTheRoundInFront(handedBackAgain)).isFalse();
    }

    @Test
    void takesAVerdictAskedAboutTheHandBackInFrontAsAboutIt(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(5_000).build();
        reviews.write(task, new MasterReview.Review(5_000, "", List.of()), "");

        assertThat(reviews.readsTheRoundInFront(task)).isTrue();
    }

    @Test
    void makesARestampedVerdictAnswerTheHandBackInFront(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(5_000).build();
        reviews.write(task, new MasterReview.Review(1_000, "abc123", List.of()), "");

        boolean restamped = reviews.restamp(task);

        assertThat(restamped).isTrue();
        assertThat(reviews.readsTheRoundInFront(task)).isTrue();
    }

    @Test
    void saysNothingWasRestampedWhereNoVerdictExists(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.restamp(task)).isFalse();
    }

    @Test
    void saysAVerdictInWordsWithASpaceWhereTheKindHasAnUnderscore() {
        assertThat(new MasterReview.Verdict(MasterReview.Kind.NOT_READY, List.of(), List.of(), List.of(), 1, "").said())
                .isEqualTo("not ready");
    }
}

package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
        reviews.write(task, new MasterReview.Review(1, "", "", "", List.of(
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

        reviews.write(task, new MasterReview.Review(1, "", "", "", List.of()), "VERDICT: ready\n");

        assertThat(worktree.resolve(MasterReview.FILE)).content().isEqualTo("VERDICT: ready\n");
    }

    @Test
    void doesNotTakeAVerdictAskedAboutAnEarlierHandBackForTheOneInFrontHoweverLateItWasWritten(@TempDir Path worktree) {
        TaskState handedBackAgain = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").build().withTurn(new Turn(Turn.Holder.MASTER, "round-not-ready", 5, "", 5_000, Map.of(), List.of()));
        reviews.write(handedBackAgain, new MasterReview.Review(3, "", "", "", List.of()), "");

        assertThat(reviews.readsTheRoundInFront(handedBackAgain)).isFalse();
    }

    @Test
    void takesAVerdictAskedAboutTheHandBackInFrontAsAboutIt(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .build().withTurn(new Turn(Turn.Holder.MASTER, "round-not-ready", 5, "", 5_000, Map.of(), List.of()));
        reviews.write(task, new MasterReview.Review(5, "", "", "", List.of()), "");

        assertThat(reviews.readsTheRoundInFront(task)).isTrue();
    }

    @Test
    void doesNotTakeAVerdictForAHandBackTheSessionMadeAgainWithNoRelayBetween(@TempDir Path worktree) {
        Turn handedBack = new Turn(Turn.Holder.MASTER, "round-not-ready", 5, "", 5_000, Map.of(), List.of());
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .build().withTurn(handedBack);
        reviews.write(task, new MasterReview.Review(5, "", "", "", List.of()), "");

        TaskState handedBackAgain = task.withTurn(handedBack.closed(Turn.Holder.SESSION, 6_000)
                .closed(Turn.Holder.MASTER, 7_000));

        assertThat(reviews.readsTheRoundInFront(handedBackAgain)).isFalse();
    }

    @Test
    void makesARestampedVerdictAnswerTheHandBackInFront(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .build().withTurn(new Turn(Turn.Holder.MASTER, "round-not-ready", 5, "", 5_000, Map.of(), List.of()));
        reviews.write(task, new MasterReview.Review(3, "abc123", "", "", List.of()), "");

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

    @Test
    void saysNothingWasWrittenForATaskWithNoWorktree() {
        TaskState task = TaskState.builder("proj", "", TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.write(task, new MasterReview.Review(1, "", "", "", List.of()), "VERDICT: ready\n")).isFalse();
    }

    @Test
    void saysTheVerdictWasNotWrittenWhereOnlyTheFileForTheHumanCould(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(".jagt"), "in the way");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        boolean written = reviews.write(task, new MasterReview.Review(1, "", "", "", List.of()), "VERDICT: ready\n");

        assertThat(written).isFalse();
        assertThat(worktree.resolve(MasterReview.FILE)).content().isEqualTo("VERDICT: ready\n");
    }

    @Test
    void saysBothFilesWereWrittenWhereTheyWere(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        assertThat(reviews.write(task, new MasterReview.Review(1, "", "", "", List.of()), "VERDICT: ready\n")).isTrue();
    }

    @Test
    void loadsAVerdictStoredBeforeItKeptTheReportKindAsOneThatMatchesNoHandBack(@TempDir Path worktree) throws Exception {
        Files.createDirectories(worktree.resolve(".jagt"));
        Files.writeString(worktree.resolve(".jagt").resolve(MasterReview.STORED),
                "{\"round\":3,\"ownDiff\":\"abc123\",\"reads\":[]}");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();

        MasterReview.Review stored = reviews.stored(task).orElseThrow();

        assertThat(stored.readsInputsOf(task, "abc123", "in1")).isFalse();
    }

    @Test
    void remembersTheKindOfTheHandBackItReadBesideTheOwnDiff(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .report(dev.jagt.orchestrator.task.AgentReport.NO_CHANGES).build();
        reviews.write(task, MasterReview.Review.of(task, "abc123", "in1", List.of()), "");

        MasterReview.Review stored = reviews.stored(task).orElseThrow();

        assertThat(stored.readsInputsOf(task, "abc123", "in1")).isTrue();
        assertThat(stored.readsInputsOf(task, "def456", "in1")).isFalse();
    }

    @Test
    void takesOnlyAReadyVerdictAsReady() {
        var ready = new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, "");
        var notReady = new MasterReview.Verdict(MasterReview.Kind.NOT_READY, List.of(), List.of(), List.of(), 1, "");

        assertThat(ready.ready()).isTrue();
        assertThat(notReady.ready()).isFalse();
    }
}

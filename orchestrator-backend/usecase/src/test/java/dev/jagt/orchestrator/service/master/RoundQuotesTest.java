package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoundQuotesTest {

    private final RoundFacts facts = mock(RoundFacts.class);
    private final MasterReview reviews = new MasterReview();
    private final RoundQuotes quotes = new RoundQuotes(facts, mock(MasterDecisions.class), reviews);

    @Test
    void keepsAReadyVerdictWhenOnlyTheBaseMovedUnderTheTasksOwnDiff(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(5_000).build();
        reviews.write(task, new MasterReview.Review(1_000, "abc123", "PLAIN", "in1", List.of(new MasterReview.Read("qa",
                new Judgement("", "ready", List.of(), "", List.of(), "")))), "");
        when(facts.ownDiff(task)).thenReturn(Optional.of("abc123"));

        boolean carried = quotes.carried(task, "in1");

        assertThat(carried).isTrue();
        assertThat(reviews.readsTheRoundInFront(task)).isTrue();
    }

    @Test
    void readsTheRoundAnewWhenTheTasksOwnDiffChanged(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(5_000).build();
        reviews.write(task, new MasterReview.Review(1_000, "abc123", "PLAIN", "in1", List.of(new MasterReview.Read("qa",
                new Judgement("", "ready", List.of(), "", List.of(), "")))), "");
        when(facts.ownDiff(task)).thenReturn(Optional.of("def456"));

        boolean carried = quotes.carried(task, "in1");

        assertThat(carried).isFalse();
        assertThat(reviews.readsTheRoundInFront(task)).isFalse();
    }

    @Test
    void keepsANotReadyVerdictWhenTheSessionHandedBackTheSameOwnDiffAndKind(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .report(AgentReport.NO_CHANGES).lastActiveTimestamp(5_000).build();
        reviews.write(task, new MasterReview.Review(1_000, "abc123", "NO_CHANGES", "in1", List.of(new MasterReview.Read("qa",
                new Judgement("", "not ready", List.of(new Finding("Foo.java", "drops v2", "", "wrong", "", "missing")), "", List.of(), "")))), "");
        when(facts.ownDiff(task)).thenReturn(Optional.of("abc123"));

        boolean carried = quotes.carried(task, "in1");

        assertThat(carried).isTrue();
        assertThat(reviews.of(task).orElseThrow().kind()).isEqualTo(MasterReview.Kind.NOT_READY);
    }

    @Test
    void readsTheRoundAnewWhenTheHandBackIsOfAnotherKind(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .report(AgentReport.NO_CHANGES).lastActiveTimestamp(5_000).build();
        reviews.write(task, new MasterReview.Review(1_000, "abc123", "PLAIN", "in1", List.of(new MasterReview.Read("qa",
                new Judgement("", "ready", List.of(), "", List.of(), "")))), "");
        when(facts.ownDiff(task)).thenReturn(Optional.of("abc123"));

        boolean carried = quotes.carried(task, "in1");

        assertThat(carried).isFalse();
    }

    @Test
    void readsTheRoundAnewWhereTheVerdictWasStoredBeforeItKeptTheReportKind(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(5_000).build();
        reviews.write(task, new MasterReview.Review(1_000, "abc123", null, "in1", List.of(new MasterReview.Read("qa",
                new Judgement("", "ready", List.of(), "", List.of(), "")))), "");
        when(facts.ownDiff(task)).thenReturn(Optional.of("abc123"));

        boolean carried = quotes.carried(task, "in1");

        assertThat(carried).isFalse();
    }

    @Test
    void readsTheRoundAnewWhenTheHumanSaidSomethingNewOverTheSameDiffAndKind(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(5_000).build();
        when(facts.ask("ABC-1", task)).thenReturn("Summary");
        when(facts.notes(task)).thenReturn("");
        when(facts.ownDiff(task)).thenReturn(Optional.of("abc123"));
        when(facts.humanSaid(task)).thenReturn(Optional.of(List.of("keep v2")));
        String read = quotes.inputs("ABC-1", task).orElseThrow();
        reviews.write(task, new MasterReview.Review(1_000, "abc123", "PLAIN", read, List.of(new MasterReview.Read("qa",
                new Judgement("", "ready", List.of(), "", List.of(), "")))), "");
        when(facts.humanSaid(task)).thenReturn(Optional.of(List.of("keep v2", "drop v1")));

        boolean carried = quotes.carried(task, quotes.inputs("ABC-1", task).orElseThrow());

        assertThat(carried).isFalse();
    }

    @Test
    void readsTheRoundAnewWhenTheSessionsNotesChangedOverTheSameDiffAndKind(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(5_000).build();
        when(facts.ask("ABC-1", task)).thenReturn("Summary");
        when(facts.humanSaid(task)).thenReturn(Optional.of(List.of()));
        when(facts.ownDiff(task)).thenReturn(Optional.of("abc123"));
        when(facts.notes(task)).thenReturn("");
        String read = quotes.inputs("ABC-1", task).orElseThrow();
        reviews.write(task, new MasterReview.Review(1_000, "abc123", "PLAIN", read, List.of(new MasterReview.Read("qa",
                new Judgement("", "ready", List.of(), "", List.of(), "")))), "");
        when(facts.notes(task)).thenReturn("disputed: the guard is right");

        boolean carried = quotes.carried(task, quotes.inputs("ABC-1", task).orElseThrow());

        assertThat(carried).isFalse();
    }

    @Test
    void quotesTheTicketTheDiffTheDecisionsTheHumanAndTheNotesOfTheRound(@TempDir Path worktree) {
        MasterDecisions decisions = mock(MasterDecisions.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(facts.humanSaid(task)).thenReturn(Optional.of(List.of("keep v2")));
        when(facts.ask("ABC-1", task)).thenReturn("Summary: accept v3 calls");
        when(facts.diff(task)).thenReturn("+int x;\n");
        when(facts.notes(task)).thenReturn("disputed: the guard is right");
        when(decisions.of(task)).thenReturn("- asked: keep v2? — decided: keep it");

        var read = new RoundQuotes(facts, decisions, reviews).round("ABC-1", task);

        assertThat(read).contains(new MasterPanel.RoundRead("Summary: accept v3 calls", "+int x;\n",
                "- asked: keep v2? — decided: keep it", List.of("keep v2"), "disputed: the guard is right"));
    }

    @Test
    void quotesAPlanRoundWithNoDiff(@TempDir Path worktree) {
        MasterDecisions decisions = mock(MasterDecisions.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();
        when(facts.humanSaid(task)).thenReturn(Optional.of(List.of("keep v2")));
        when(facts.ask("ABC-1", task)).thenReturn("Summary: accept v3 calls");
        when(facts.diff(task)).thenReturn("+int x;\n");
        when(facts.notes(task)).thenReturn("disputed: the guard is right");
        when(decisions.of(task)).thenReturn("");

        var read = new RoundQuotes(facts, decisions, reviews).plan("ABC-1", task);

        assertThat(read).contains(new MasterPanel.RoundRead("Summary: accept v3 calls", "", "", List.of("keep v2"),
                "disputed: the guard is right"));
    }

    @Test
    void quotesAQuestionRoundWithOnlyTheDecisionsAndWhatTheHumanSaid(@TempDir Path worktree) {
        MasterDecisions decisions = mock(MasterDecisions.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();
        when(facts.humanSaid(task)).thenReturn(Optional.of(List.of("keep v2")));
        when(decisions.of(task)).thenReturn("- settled");

        var read = new RoundQuotes(facts, decisions, reviews).question(task);

        assertThat(read).contains(new MasterPanel.RoundRead("", "", "- settled", List.of("keep v2"), ""));
    }

    @Test
    void quotesNoRoundWhereWhatTheHumanTypedCouldNotBeRead(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(facts.humanSaid(task)).thenReturn(Optional.empty());

        assertThat(quotes.round("ABC-1", task)).isEmpty();
        assertThat(quotes.plan("ABC-1", task)).isEmpty();
        assertThat(quotes.question(task)).isEmpty();
    }

    @Test
    void passesOnTheChangedPathsTheOwnDiffAndThePlanAsTheFactsReadThem(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();
        when(facts.paths(task)).thenReturn(Optional.of(List.of("A.java")));
        when(facts.ownDiff(task)).thenReturn(Optional.of("abc123"));
        when(facts.plan(task)).thenReturn("1. add the v3 route");

        assertThat(quotes.paths(task)).contains(List.of("A.java"));
        assertThat(quotes.ownDiff(task)).contains("abc123");
        assertThat(quotes.planFile(task)).isEqualTo("1. add the v3 route");
    }
}

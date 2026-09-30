package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterVerdictsTest {

    private final AgentSessions sessions = mock(AgentSessions.class);
    private final CommandService commands = mock(CommandService.class);
    private final FlowReports reports = mock(FlowReports.class);
    private final MasterDecisions decisions = mock(MasterDecisions.class);
    private final MasterVerdicts verdicts = new MasterVerdicts(sessions, commands, reports, decisions);

    private static ConfigService.ConfigFile.MasterConfig acting() {
        return new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
    }

    private static ConfigService.ConfigFile.MasterConfig judging() {
        return new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null);
    }

    private static TaskState in(Path worktree) {
        return TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
    }

    @Test
    void sendsARoundThatIsNotReadyBackToWhoeverWroteTheCode(@TempDir Path worktree) {
        verdicts.act("ABC-1", in(worktree), new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("Foo.java:12 the guard is inverted"), 1), acting());

        verify(sessions).relayIfChanged(eq("ABC-1"), contains("the guard is inverted"));
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void takesANotReadyRoundBackToWorkSoTheFixedOneIsReadAgain(@TempDir Path worktree) {
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);

        verdicts.act("ABC-1", in(worktree), new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("Foo.java:12 the guard is inverted"), 1), acting());

        verify(reports).report(eq("ABC-1"), eq(TaskStatus.IN_PROGRESS), anyString());
    }

    @Test
    void putsTheSessionBackToWorkOnTheAnswerTheMasterGaveIt(@TempDir Path worktree) {
        when(sessions.relayIfChanged(eq("ABC-1"), contains("keep v2 beside v3"))).thenReturn(true);

        verdicts.answered("ABC-1", in(worktree), "keep v2?", "keep v2 beside v3");

        verify(reports).report("ABC-1", TaskStatus.IN_PROGRESS, "master answered the question");
    }

    @Test
    void settlesWhatANotReadyRoundSentBackSoTheNextRoundDoesNotReopenIt(@TempDir Path worktree) {
        TaskState task = in(worktree);
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);

        verdicts.act("ABC-1", task, new MasterReview.Verdict(MasterReview.Kind.NOT_READY,
                List.of("Foo.java:12 the guard is inverted"), 1), acting());

        verify(decisions).record(task, "Foo.java:12 the guard is inverted");
    }

    @Test
    void shipsAReadyRoundOnlyWhereAHumanSaidTheReviewerStandsInForThem(@TempDir Path worktree) {
        verdicts.act("ABC-1", in(worktree), new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1), acting());

        verify(commands).execute("ABC-1", TaskAction.SHIP);
    }

    @Test
    void shipsNothingWhereTheHumanKeptThatStep(@TempDir Path worktree) {
        var kept = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("ship"), null);

        boolean moved = verdicts.act("ABC-1", in(worktree), new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1),
                kept);

        assertThat(moved).isFalse();
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void leavesAReadyRoundForTheHumanWhileTheReviewerOnlyJudges(@TempDir Path worktree) {
        boolean moved = verdicts.act("ABC-1", in(worktree), new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), 1),
                judging());

        assertThat(moved).isFalse();
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void putsTheReviewersQuestionToTheHumanThroughTheSessionThatWaitsOnTheAnswer(@TempDir Path worktree) {
        when(sessions.relayIfChanged(eq("ABC-1"), contains("Add v3 beside v2, or replace it?"))).thenReturn(true);

        verdicts.act("ABC-1", in(worktree), new MasterReview.Verdict(MasterReview.Kind.QUESTION,
                List.of("Foo.java drops v2", "Add v3 beside v2, or replace it?"), 1), acting());

        verify(reports).report("ABC-1", TaskStatus.IN_PROGRESS,
                "outcome=question — reviewer: Add v3 beside v2, or replace it?");
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void asksTheSameQuestionOnce(@TempDir Path worktree) {
        boolean moved = verdicts.act("ABC-1", in(worktree), new MasterReview.Verdict(MasterReview.Kind.QUESTION,
                List.of("Add v3 beside v2, or replace it?"), 1), acting());

        assertThat(moved).isFalse();
        verify(reports, never()).report(anyString(), any(), anyString());
    }
}

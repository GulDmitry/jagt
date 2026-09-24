package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MasterVerdictsTest {

    private final AgentSessions sessions = mock(AgentSessions.class);
    private final CommandService commands = mock(CommandService.class);
    private final MasterVerdicts verdicts = new MasterVerdicts(new MasterReview(), sessions, commands);

    private static TaskState in(Path worktree) {
        return TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
    }

    @Test
    void sendsARoundThatIsNotReadyBackToWhoeverWroteTheCode(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(MasterReview.FILE),
                "Foo.java:12 the guard is inverted\nVERDICT: not ready\n");

        verdicts.act("ABC-1", in(worktree), new MasterReview.Verdict(false, "not ready", 1), MasterMode.ACT);

        verify(sessions).relayIfChanged(eq("ABC-1"), contains("the guard is inverted"));
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void shipsAReadyRoundOnlyWhereAHumanSaidTheReviewerStandsInForThem(@TempDir Path worktree) {
        verdicts.act("ABC-1", in(worktree), new MasterReview.Verdict(true, "ready", 1), MasterMode.ACT);

        verify(commands).execute("ABC-1", TaskAction.SHIP);
    }

    @Test
    void leavesAReadyRoundForTheHumanWhileTheReviewerOnlyJudges(@TempDir Path worktree) {
        boolean moved = verdicts.act("ABC-1", in(worktree), new MasterReview.Verdict(true, "ready", 1),
                MasterMode.JUDGE);

        assertThat(moved).isFalse();
        verify(commands, never()).execute(anyString(), any());
    }
}

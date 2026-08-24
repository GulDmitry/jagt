package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.AgentSpend;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentSpendReaderTest {

    private final StateService stateService = mock(StateService.class);
    private final SessionLog sessionLog = mock(SessionLog.class);

    @Test
    void readsTheLogFromWhereThisTaskLastCountedIt(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, "x".repeat(500));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withAgentSpend(AgentSpend.NONE.plus(TokenUsage.ofCall(5, 0, 1, 0), name(log), 200));
        when(stateService.task("ABC-42")).thenReturn(Optional.of(task));
        when(sessionLog.spent(eq(log), eq(200L), eq(300L)))
                .thenReturn(new SessionLog.Spent(TokenUsage.ofCall(7, 200, 20, 0), 500));

        new AgentSpendReader(stateService, sessionLog).charge("ABC-42", log);

        AgentSpend booked = applied(task);
        assertThat(booked.usageOrNone()).isEqualTo(new TokenUsage(2, 12, 200, 21, 0));
        assertThat(booked.markFor(name(log))).isEqualTo(500);
    }

    /**
     * Two hook reports land at once — one counts the window, the other has already booked it. Adding both would
     * charge the same turns twice, so the loser drops what it counted.
     */
    @Test
    void dropsAWindowAnotherReportHasAlreadyBooked(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, "x".repeat(400));
        when(stateService.task("ABC-42")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()));
        when(sessionLog.spent(eq(log), eq(0L), anyLong()))
                .thenReturn(new SessionLog.Spent(TokenUsage.ofCall(7, 0, 20, 0), 400));
        TaskState bookedMeanwhile = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withAgentSpend(AgentSpend.NONE.plus(TokenUsage.ofCall(7, 0, 20, 0), name(log), 400));

        new AgentSpendReader(stateService, sessionLog).charge("ABC-42", log);

        assertThat(applied(bookedMeanwhile).usageOrNone()).isEqualTo(new TokenUsage(1, 7, 0, 20, 0));
    }

    /** A log rewritten under jagt cannot say what of it was counted; its total stands and the mark follows. */
    @Test
    void countsNothingFromALogThatShrankAndKeepsWhatItAlreadyCost(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, "x".repeat(50));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withAgentSpend(AgentSpend.NONE.plus(TokenUsage.ofCall(9, 0, 3, 0), name(log), 900));
        when(stateService.task("ABC-42")).thenReturn(Optional.of(task));

        new AgentSpendReader(stateService, sessionLog).charge("ABC-42", log);

        AgentSpend booked = applied(task);
        assertThat(booked.usageOrNone()).isEqualTo(new TokenUsage(1, 9, 0, 3, 0));
        assertThat(booked.markFor(name(log))).isEqualTo(50);
        verifyNoInteractions(sessionLog);
    }

    /**
     * A task can have two logs alive at once — a second session opened on one whose first is hung. One shared
     * mark had them re-read each other from nothing on every report.
     */
    @Test
    void keepsAMarkPerLogSoTwoLiveSessionsDoNotRecountEachOther(@TempDir Path dir) throws IOException {
        Path second = dir.resolve("second.jsonl");
        Files.writeString(second, "x".repeat(120));
        TaskState afterFirst = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withAgentSpend(AgentSpend.NONE.plus(TokenUsage.ofCall(5, 0, 1, 0),
                        name(dir.resolve("first.jsonl")), 700));
        when(stateService.task("ABC-42")).thenReturn(Optional.of(afterFirst));
        when(sessionLog.spent(eq(second), eq(0L), eq(120L)))
                .thenReturn(new SessionLog.Spent(TokenUsage.ofCall(3, 0, 2, 0), 120));

        new AgentSpendReader(stateService, sessionLog).charge("ABC-42", second);

        AgentSpend booked = applied(afterFirst);
        assertThat(booked.markFor(name(dir.resolve("first.jsonl")))).isEqualTo(700);
        assertThat(booked.markFor(name(second))).isEqualTo(120);
        assertThat(booked.usageOrNone().calls()).isEqualTo(2);
    }

    @Test
    void chargesNothingForALogThatIsNotThere(@TempDir Path dir) {
        new AgentSpendReader(stateService, sessionLog).charge("ABC-42", dir.resolve("gone.jsonl"));

        verify(stateService, never()).updateTask(eq("ABC-42"), any());
    }

    @Test
    void chargesNothingWhenTheLogHasNotGrownSinceTheLastRead(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, "x".repeat(300));
        when(stateService.task("ABC-42")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                        .withAgentSpend(AgentSpend.NONE.plus(TokenUsage.ofCall(1, 0, 1, 0), name(log), 300))));

        new AgentSpendReader(stateService, sessionLog).charge("ABC-42", log);

        verify(stateService, never()).updateTask(eq("ABC-42"), any());
    }

    private static String name(Path log) {
        return log.toAbsolutePath().normalize().toString();
    }

    private AgentSpend applied(TaskState to) {
        ArgumentCaptor<UnaryOperator<TaskState>> update = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-42"), update.capture());
        return update.getValue().apply(to).agentSpendOrNone();
    }
}

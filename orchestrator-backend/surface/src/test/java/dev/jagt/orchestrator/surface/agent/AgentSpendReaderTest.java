package dev.jagt.orchestrator.surface.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.agent.SessionLog;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.usage.AgentSpend;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class AgentSpendReaderTest {

  private final StateService stateService = mock(StateService.class);
  private final SessionLog sessionLog = mock(SessionLog.class);
  private final AgentSpendReader reader = new AgentSpendReader(stateService, sessionLog);
  private final ArgumentCaptor<UnaryOperator<TaskState>> update = ArgumentCaptor.captor();
  private final TaskState inProgress =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

  @Test
  void readsTheLogFromWhereThisTaskLastCountedIt(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(log, "x".repeat(500));
    TaskState task =
        inProgress.withAgentSpend(
            AgentSpend.NONE.plus(TokenUsage.ofCall(5, 0, 1, 0), log.toString(), 200));
    when(stateService.task("ABC-42")).thenReturn(Optional.of(task));
    when(sessionLog.spent(eq(log), eq(200L), eq(300L)))
        .thenReturn(new SessionLog.Spent(TokenUsage.ofCall(7, 200, 20, 0), 500));

    reader.charge("ABC-42", log);

    verify(stateService).updateTask(eq("ABC-42"), update.capture());
    AgentSpend booked = update.getValue().apply(task).gate().agentSpendOrNone();
    assertThat(booked.usageOrNone()).isEqualTo(new TokenUsage(2, 12, 200, 21, 0));
    assertThat(booked.markFor(log.toString())).isEqualTo(500);
  }

  @Test
  void dropsWindowAnotherReportHasAlreadyBooked(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(log, "x".repeat(400));
    when(stateService.task("ABC-42")).thenReturn(Optional.of(inProgress));
    when(sessionLog.spent(eq(log), eq(0L), anyLong()))
        .thenReturn(new SessionLog.Spent(TokenUsage.ofCall(7, 0, 20, 0), 400));
    TaskState bookedMeanwhile =
        inProgress.withAgentSpend(
            AgentSpend.NONE.plus(TokenUsage.ofCall(7, 0, 20, 0), log.toString(), 400));

    reader.charge("ABC-42", log);

    verify(stateService).updateTask(eq("ABC-42"), update.capture());
    assertThat(update.getValue().apply(bookedMeanwhile).gate().agentSpendOrNone().usageOrNone())
        .isEqualTo(new TokenUsage(1, 7, 0, 20, 0));
  }

  @Test
  void countsNothingFromLogThatShrankAndKeepsWhatItAlreadyCost(@TempDir Path dir)
      throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(log, "x".repeat(50));
    TaskState task =
        inProgress.withAgentSpend(
            AgentSpend.NONE.plus(TokenUsage.ofCall(9, 0, 3, 0), log.toString(), 900));
    when(stateService.task("ABC-42")).thenReturn(Optional.of(task));

    reader.charge("ABC-42", log);

    verify(stateService).updateTask(eq("ABC-42"), update.capture());
    AgentSpend booked = update.getValue().apply(task).gate().agentSpendOrNone();
    assertThat(booked.usageOrNone()).isEqualTo(new TokenUsage(1, 9, 0, 3, 0));
    assertThat(booked.markFor(log.toString())).isEqualTo(50);
    verifyNoInteractions(sessionLog);
  }

  @Test
  void keepsMarkPerLogSoTwoLiveSessionsDoNotRecountEachOther(@TempDir Path dir) throws IOException {
    Path second = dir.resolve("second.jsonl");
    Files.writeString(second, "x".repeat(120));
    TaskState afterFirst =
        inProgress.withAgentSpend(
            AgentSpend.NONE.plus(
                TokenUsage.ofCall(5, 0, 1, 0), dir.resolve("first.jsonl").toString(), 700));
    when(stateService.task("ABC-42")).thenReturn(Optional.of(afterFirst));
    when(sessionLog.spent(eq(second), eq(0L), eq(120L)))
        .thenReturn(new SessionLog.Spent(TokenUsage.ofCall(3, 0, 2, 0), 120));

    reader.charge("ABC-42", second);

    verify(stateService).updateTask(eq("ABC-42"), update.capture());
    AgentSpend booked = update.getValue().apply(afterFirst).gate().agentSpendOrNone();
    assertThat(booked.markFor(dir.resolve("first.jsonl").toString())).isEqualTo(700);
    assertThat(booked.markFor(second.toString())).isEqualTo(120);
    assertThat(booked.usageOrNone().calls()).isEqualTo(2);
  }

  @Test
  void chargesNothingForLogThatIsNotThere(@TempDir Path dir) {
    reader.charge("ABC-42", dir.resolve("gone.jsonl"));

    verify(stateService, never()).updateTask(eq("ABC-42"), any());
  }

  @Test
  void chargesNothingWhenTheGrownLogHoldsNoCompleteCallYet(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(log, "x".repeat(350));
    when(stateService.task("ABC-42"))
        .thenReturn(
            Optional.of(
                inProgress.withAgentSpend(
                    AgentSpend.NONE.plus(TokenUsage.ofCall(1, 0, 1, 0), log.toString(), 300))));
    when(sessionLog.spent(eq(log), eq(300L), eq(50L)))
        .thenReturn(new SessionLog.Spent(TokenUsage.NONE, 300));

    reader.charge("ABC-42", log);

    verify(stateService, never()).updateTask(eq("ABC-42"), any());
  }

  @Test
  void chargesNothingWhenTheLogHasNotGrownSinceTheLastRead(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(log, "x".repeat(300));
    when(stateService.task("ABC-42"))
        .thenReturn(
            Optional.of(
                inProgress.withAgentSpend(
                    AgentSpend.NONE.plus(TokenUsage.ofCall(1, 0, 1, 0), log.toString(), 300))));

    reader.charge("ABC-42", log);

    verify(stateService, never()).updateTask(eq("ABC-42"), any());
  }
}

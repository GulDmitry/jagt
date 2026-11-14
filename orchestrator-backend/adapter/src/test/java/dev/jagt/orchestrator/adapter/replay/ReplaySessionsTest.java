package dev.jagt.orchestrator.adapter.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.RoleRecord;
import dev.jagt.orchestrator.task.turn.Turn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplaySessionsTest {

  private static final int FEW_TICKS = 4;
  private static final long WAIT_MILLIS = 10_000;

  private final Checkers checkers = new Checkers(new TaskChart(Composition.builtIn()));
  private static final String SHIP_AND_PUSH =
      """
      {"type":"turn","task":"ABC-1","turn":1,"step":"ship"}
      {"type":"push","task":"ABC-1","turn":1,"message":"Fix the widget","paths":["a.txt"]}
      """;

  private final TaskState newTask = TaskState.builder("p", "/work/ABC-1", TaskStatus.NEW).build();
  private final TaskStore tasks = mock(TaskStore.class);
  private final ReplayMoves moves = mock(ReplayMoves.class);
  private @Nullable ReplaySessions sessions;

  @AfterEach
  void stopTheTicker() {
    if (sessions != null) {
      sessions.stop();
    }
  }

  @Test
  void launchedSessionIsAnsweredWithTheMovesRecordedBeforeItsFirstTurnOnce(@TempDir Path root)
      throws Exception {
    Path file =
        Files.writeString(
            root.resolve("recording.jsonl"),
            """
            {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"done"}}
            """);
    Recording recording = new Recording(file.toString());
    when(tasks.task("ABC-1")).thenReturn(Optional.of(newTask));
    sessions = new ReplaySessions(recording, tasks, moves, checkers);
    sessions.launched(Path.of("/work/ABC-1"));

    sessions.start();

    verify(moves, timeout(WAIT_MILLIS))
        .make(eq(Path.of("/work/ABC-1")), any(RecordingLine.Wrote.class));
    verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
    verify(moves, times(1)).make(any(), any());
    assertThat(recording.diverged()).isFalse();
  }

  @Test
  void sessionThatNeverLaunchedOrHasNoWorktreeYetIsLeftAlone(@TempDir Path root) throws Exception {
    when(tasks.task("ABC-3"))
        .thenReturn(Optional.of(TaskState.builder("p", null, TaskStatus.NEW).build()));
    when(tasks.task("ABC-2"))
        .thenReturn(Optional.of(TaskState.builder("p", "/work/ABC-2", TaskStatus.NEW).build()));
    when(tasks.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("p", "/work/ABC-1", TaskStatus.NEW).build()));
    Recording recording =
        new Recording(
            Files.writeString(
                    root.resolve("recording.jsonl"),
                    """
                    {"type":"tree","task":"ABC-3","turn":0,"files":{"notes.md":"three"}}
                    {"type":"tree","task":"ABC-2","turn":0,"files":{"notes.md":"two"}}
                    {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"one"}}
                    """)
                .toString());
    sessions = new ReplaySessions(recording, tasks, moves, checkers);
    sessions.launched(Path.of("/work/ABC-1"));

    sessions.start();

    verify(moves, timeout(WAIT_MILLIS)).make(eq(Path.of("/work/ABC-1")), any());
    verify(moves, times(1)).make(any(), any());
  }

  @Test
  void replayThatAlreadyDivergedMakesNoMoreMoves(@TempDir Path root) throws Exception {
    Path file =
        Files.writeString(
            root.resolve("recording.jsonl"),
            """
            {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"one"}}
            """);
    Recording recording = new Recording(file.toString());
    when(tasks.task("ABC-1")).thenReturn(Optional.of(newTask));
    sessions = new ReplaySessions(recording, tasks, moves, checkers);
    recording.diverge("ABC-2", "ship", "a read the recording never held");
    sessions.launched(Path.of("/work/ABC-1"));

    sessions.start();

    verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
    verify(moves, never()).make(any(), any());
  }

  @Test
  void turnJagtOpensIsAnsweredWithItsRecordedMovesOnceHoweverOftenItIsPolled(@TempDir Path root)
      throws Exception {
    Path file = Files.writeString(root.resolve("recording.jsonl"), SHIP_AND_PUSH);
    Recording recording = new Recording(file.toString());
    Turn shipping = Turn.none().relayed("ship", "facts", "", 1);
    when(tasks.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("p", "/work/ABC-1", TaskStatus.IN_PROGRESS)
                    .turn(shipping)
                    .build()));
    sessions = new ReplaySessions(recording, tasks, moves, checkers);
    sessions.launched(Path.of("/work/ABC-1"));

    sessions.start();

    verify(moves, timeout(WAIT_MILLIS))
        .make(eq(Path.of("/work/ABC-1")), any(RecordingLine.Pushed.class));
    verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
    verify(moves, times(1)).make(any(), any());
    assertThat(recording.diverged()).isFalse();
  }

  @Test
  void turnNobodyOpenedYetIsNotAnsweredNorDivergence(@TempDir Path root) throws Exception {
    Path file = Files.writeString(root.resolve("recording.jsonl"), SHIP_AND_PUSH);
    Recording recording = new Recording(file.toString());
    when(tasks.task("ABC-1")).thenReturn(Optional.of(newTask));
    sessions = new ReplaySessions(recording, tasks, moves, checkers);
    sessions.launched(Path.of("/work/ABC-1"));

    sessions.start();

    verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
    verify(moves, never()).make(any(), any());
    assertThat(recording.diverged()).isFalse();
  }

  @Test
  void turnOpenedAtAnotherStepThanRecordedEndsTheReplayWithoutItsMoves(@TempDir Path root)
      throws Exception {
    Path file = Files.writeString(root.resolve("recording.jsonl"), SHIP_AND_PUSH);
    Recording recording = new Recording(file.toString());
    Turn verifying = Turn.none().relayed("verify", "facts", "", 1);
    when(tasks.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("p", "/work/ABC-1", TaskStatus.IN_PROGRESS)
                    .turn(verifying)
                    .build()));
    sessions = new ReplaySessions(recording, tasks, moves, checkers);
    sessions.launched(Path.of("/work/ABC-1"));

    sessions.start();

    verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
    assertThat(recording.diverged()).isTrue();
    verify(moves, never()).make(any(), any());
  }

  @Test
  void turnCheckersReadHoldsIsNeitherAnsweredNorDivergence(@TempDir Path root) throws Exception {
    Path file = Files.writeString(root.resolve("recording.jsonl"), SHIP_AND_PUSH);
    Recording recording = new Recording(file.toString());
    Turn checking = Turn.none().relayed("accept", "facts", "", 1);
    when(tasks.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("p", "/work/ABC-1", TaskStatus.DEPLOYED)
                    .turn(checking)
                    .build()
                    .withRole("acceptance", RoleRecord.defaults().withReading("accept"))));
    sessions = new ReplaySessions(recording, tasks, moves, checkers);
    sessions.launched(Path.of("/work/ABC-1"));

    sessions.start();

    verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
    assertThat(recording.diverged()).isFalse();
    verify(moves, never()).make(any(), any());
  }

  @Test
  void replayThatFailsInsideTheTaskStoreEndsTheReplay(@TempDir Path root) throws Exception {
    Path file =
        Files.writeString(
            root.resolve("recording.jsonl"),
            """
            {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"one"}}
            """);
    Recording recording = new Recording(file.toString());
    when(tasks.task("ABC-1")).thenThrow(new IllegalStateException("state unreadable"));
    sessions = new ReplaySessions(recording, tasks, moves, checkers);

    sessions.start();

    verify(tasks, timeout(WAIT_MILLIS).atLeast(2)).task("ABC-1");
    assertThat(recording.diverged()).isTrue();
  }

  @Test
  void recordingWithNoSessionMovesAsksTheTaskStoreForNoTask(@TempDir Path root) throws Exception {
    Path file =
        Files.writeString(
            root.resolve("recording.jsonl"),
            """
            {"type":"read","kind":"TICKET_READ","ref":"ABC-1","answer":{"title":"first"}}
            {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"one"}}
            """);
    when(tasks.task("ABC-1")).thenReturn(Optional.of(newTask));
    sessions = new ReplaySessions(new Recording(file.toString()), tasks, moves, checkers);

    sessions.start();

    verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
    verify(tasks, never()).task("");
  }

  @Test
  void drainsTheRecordingWhenEveryRecordedMoveWasMade(@TempDir Path root) throws Exception {
    Path file =
        Files.writeString(
            root.resolve("recording.jsonl"),
            """
            {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"one"}}
            """);
    Recording recording = new Recording(file.toString());
    when(tasks.task("ABC-1")).thenReturn(Optional.of(newTask));
    sessions = new ReplaySessions(recording, tasks, moves, checkers);
    sessions.launched(Path.of("/work/ABC-1"));

    sessions.start();

    await().atMost(Duration.ofSeconds(10)).until(recording::drained);
  }

  @Test
  void neverDrainsTheRecordingOnceItDiverged(@TempDir Path root) throws Exception {
    Path file =
        Files.writeString(
            root.resolve("recording.jsonl"),
            """
            {"type":"read","kind":"TICKET_READ","ref":"ABC-1","answer":{"title":"first"}}
            """);
    Recording recording = new Recording(file.toString());
    recording.diverge("", "intake", "a read the recording never held");
    sessions = new ReplaySessions(recording, tasks, moves, checkers);

    sessions.start();

    verify(tasks, after(2_000).never()).task(any());
    assertThat(recording.drained()).isFalse();
  }
}

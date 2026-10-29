package dev.jagt.orchestrator.adapter.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;

class ReplaySessionsTest {

    private static final int FEW_TICKS = 4;
    private static final long WAIT_MILLIS = 10_000;

    private ReplaySessions sessions;

    @AfterEach
    void stopTheTicker() {
        sessions.stop();
    }

    @Test
    void aLaunchedSessionIsAnsweredWithTheMovesRecordedBeforeItsFirstTurnOnce(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"done"}}
                """);
        Recording recording = new Recording(file.toString());
        TaskStore tasks = mock(TaskStore.class);
        when(tasks.task("ABC-1")).thenReturn(Optional.of(TaskState.builder("p", "/work/ABC-1", TaskStatus.NEW).build()));
        ReplayMoves moves = mock(ReplayMoves.class);
        sessions = new ReplaySessions(recording, tasks, moves);
        sessions.launched(Path.of("/work/ABC-1"));

        sessions.start();

        verify(moves, timeout(WAIT_MILLIS)).make(eq(Path.of("/work/ABC-1")), any(RecordingLine.Wrote.class));
        verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
        verify(moves, times(1)).make(any(), any());
        assertThat(recording.diverged()).isFalse();
    }

    @Test
    void aSessionThatNeverLaunchedOrHasNoWorktreeYetIsLeftAlone(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"tree","task":"ABC-3","turn":0,"files":{"notes.md":"three"}}
                {"type":"tree","task":"ABC-2","turn":0,"files":{"notes.md":"two"}}
                {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"one"}}
                """);
        TaskStore tasks = mock(TaskStore.class);
        when(tasks.task("ABC-3")).thenReturn(Optional.of(TaskState.builder("p", null, TaskStatus.NEW).build()));
        when(tasks.task("ABC-2")).thenReturn(Optional.of(TaskState.builder("p", "/work/ABC-2", TaskStatus.NEW).build()));
        when(tasks.task("ABC-1")).thenReturn(Optional.of(TaskState.builder("p", "/work/ABC-1", TaskStatus.NEW).build()));
        ReplayMoves moves = mock(ReplayMoves.class);
        sessions = new ReplaySessions(new Recording(file.toString()), tasks, moves);
        sessions.launched(Path.of("/work/ABC-1"));

        sessions.start();

        verify(moves, timeout(WAIT_MILLIS)).make(eq(Path.of("/work/ABC-1")), any());
        verify(moves, times(1)).make(any(), any());
    }

    @Test
    void aReplayThatAlreadyDivergedMakesNoMoreMoves(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"one"}}
                """);
        Recording recording = new Recording(file.toString());
        recording.diverge("ABC-2", "ship", "a read the recording never held");
        TaskStore tasks = mock(TaskStore.class);
        when(tasks.task("ABC-1")).thenReturn(Optional.of(TaskState.builder("p", "/work/ABC-1", TaskStatus.NEW).build()));
        ReplayMoves moves = mock(ReplayMoves.class);
        sessions = new ReplaySessions(recording, tasks, moves);
        sessions.launched(Path.of("/work/ABC-1"));

        sessions.start();

        verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
        verify(moves, never()).make(any(), any());
    }

    @Test
    void aTurnJagtOpensIsAnsweredWithItsRecordedMovesOnceHoweverOftenItIsPolled(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"turn","task":"ABC-1","turn":1,"step":"ship"}
                {"type":"push","task":"ABC-1","turn":1,"message":"Fix the widget","paths":["a.txt"]}
                """);
        Recording recording = new Recording(file.toString());
        Turn shipping = Turn.none().relayed("ship", "facts", "", 1);
        TaskStore tasks = mock(TaskStore.class);
        when(tasks.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("p", "/work/ABC-1", TaskStatus.IN_PROGRESS).turn(shipping).build()));
        ReplayMoves moves = mock(ReplayMoves.class);
        sessions = new ReplaySessions(recording, tasks, moves);
        sessions.launched(Path.of("/work/ABC-1"));

        sessions.start();

        verify(moves, timeout(WAIT_MILLIS)).make(eq(Path.of("/work/ABC-1")), any(RecordingLine.Pushed.class));
        verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
        verify(moves, times(1)).make(any(), any());
        assertThat(recording.diverged()).isFalse();
    }

    @Test
    void aTurnNobodyOpenedYetIsNotAnsweredNorADivergence(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"turn","task":"ABC-1","turn":1,"step":"ship"}
                {"type":"push","task":"ABC-1","turn":1,"message":"Fix the widget","paths":["a.txt"]}
                """);
        Recording recording = new Recording(file.toString());
        TaskStore tasks = mock(TaskStore.class);
        when(tasks.task("ABC-1")).thenReturn(Optional.of(TaskState.builder("p", "/work/ABC-1", TaskStatus.NEW).build()));
        ReplayMoves moves = mock(ReplayMoves.class);
        sessions = new ReplaySessions(recording, tasks, moves);
        sessions.launched(Path.of("/work/ABC-1"));

        sessions.start();

        verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
        verify(moves, never()).make(any(), any());
        assertThat(recording.diverged()).isFalse();
    }

    @Test
    void aTurnOpenedAtAnotherStepThanRecordedEndsTheReplayWithoutItsMoves(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"turn","task":"ABC-1","turn":1,"step":"ship"}
                {"type":"push","task":"ABC-1","turn":1,"message":"Fix the widget","paths":["a.txt"]}
                """);
        Recording recording = new Recording(file.toString());
        Turn verifying = Turn.none().relayed("verify", "facts", "", 1);
        TaskStore tasks = mock(TaskStore.class);
        when(tasks.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("p", "/work/ABC-1", TaskStatus.IN_PROGRESS).turn(verifying).build()));
        ReplayMoves moves = mock(ReplayMoves.class);
        sessions = new ReplaySessions(recording, tasks, moves);
        sessions.launched(Path.of("/work/ABC-1"));

        sessions.start();

        verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
        assertThat(recording.diverged()).isTrue();
        verify(moves, never()).make(any(), any());
    }

    @Test
    void aReplayThatFailsInsideTheTaskStoreEndsTheReplay(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"one"}}
                """);
        Recording recording = new Recording(file.toString());
        TaskStore tasks = mock(TaskStore.class);
        when(tasks.task("ABC-1")).thenThrow(new IllegalStateException("state unreadable"));
        sessions = new ReplaySessions(recording, tasks, mock(ReplayMoves.class));

        sessions.start();

        verify(tasks, timeout(WAIT_MILLIS).atLeast(2)).task("ABC-1");
        assertThat(recording.diverged()).isTrue();
    }

    @Test
    void aRecordingWithNoSessionMovesAsksTheTaskStoreForNoTask(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"TICKET_READ","ref":"ABC-1","answer":{"title":"first"}}
                {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"one"}}
                """);
        TaskStore tasks = mock(TaskStore.class);
        when(tasks.task("ABC-1")).thenReturn(Optional.of(TaskState.builder("p", "/work/ABC-1", TaskStatus.NEW).build()));
        sessions = new ReplaySessions(new Recording(file.toString()), tasks, mock(ReplayMoves.class));

        sessions.start();

        verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
        verify(tasks, never()).task("");
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void saysOnceThatTheReplayDrainedWhenEveryRecordedMoveWasMade(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"tree","task":"ABC-1","turn":0,"files":{"notes.md":"one"}}
                """);
        TaskStore tasks = mock(TaskStore.class);
        when(tasks.task("ABC-1")).thenReturn(Optional.of(TaskState.builder("p", "/work/ABC-1", TaskStatus.NEW).build()));
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger sessionsLog = (Logger) LoggerFactory.getLogger(ReplaySessions.class);
        sessionsLog.addAppender(log);
        sessions = new ReplaySessions(new Recording(file.toString()), tasks, mock(ReplayMoves.class));
        sessions.launched(Path.of("/work/ABC-1"));

        sessions.start();

        verify(tasks, timeout(WAIT_MILLIS).atLeast(FEW_TICKS)).task("ABC-1");
        sessionsLog.detachAppender(log);
        assertThat(log.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly("replay drained");
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void neverSaysTheReplayDrainedOnceItDiverged(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"TICKET_READ","ref":"ABC-1","answer":{"title":"first"}}
                """);
        Recording recording = new Recording(file.toString());
        recording.diverge("", "intake", "a read the recording never held");
        TaskStore tasks = mock(TaskStore.class);
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger sessionsLog = (Logger) LoggerFactory.getLogger(ReplaySessions.class);
        sessionsLog.addAppender(log);
        sessions = new ReplaySessions(recording, tasks, mock(ReplayMoves.class));

        sessions.start();

        verify(tasks, after(2_000).never()).task(any());
        sessionsLog.detachAppender(log);
        assertThat(log.list).isEmpty();
    }
}

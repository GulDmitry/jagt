package dev.jagt.orchestrator.adapter.replay;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class ReplayFixtures {

  private ReplayFixtures() {}

  static Path recordingFile(Path root, String lines) throws IOException {
    return Files.writeString(root.resolve("recording.jsonl"), lines);
  }

  static TaskState newTaskAt(String worktree) {
    return TaskState.builder("p", worktree, TaskStatus.NEW).build();
  }

  static TaskStore holding(String id, TaskState state) {
    TaskStore tasks = mock(TaskStore.class);
    when(tasks.task(id)).thenReturn(Optional.of(state));
    return tasks;
  }

  static ReplaySessions sessionsOf(Recording recording, TaskStore tasks, ReplayMoves moves) {
    return new ReplaySessions(
        recording, tasks, moves, new Checkers(new TaskChart(Composition.builtIn())));
  }

  static ListAppender<ILoggingEvent> attached(Logger logger) {
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    logger.addAppender(log);
    return log;
  }
}

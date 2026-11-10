package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TaskState;
import java.util.Map;

public interface StateEvents {

  /**
   * What the state file held before a write and what it holds after; published once the file is on
   * disk and outside the write lock.
   */
  record StateWritten(Map<String, TaskState> previous, Map<String, TaskState> tasks) {}

  void publish(StateWritten written);
}

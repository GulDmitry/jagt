package dev.jagt.orchestrator.service.turn;

/** A step was claimed against a task that no longer has the status and turn it was read at. */
public class TaskMovedOn extends RuntimeException {

  public TaskMovedOn(String taskId) {
    super(taskId + " moved on");
  }
}

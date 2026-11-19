package dev.jagt.orchestrator.service.turn;

/** A step was claimed against a task that no longer has the status and turn it was read at. */
public class TaskMovedOn extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TaskMovedOn(String taskId) {
    super(taskId + " moved on");
  }
}

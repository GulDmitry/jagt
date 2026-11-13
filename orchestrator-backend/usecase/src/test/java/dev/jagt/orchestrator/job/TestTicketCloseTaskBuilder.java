package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import lombok.With;

@With
public record TestTicketCloseTaskBuilder(TaskStatus status, List<StatusChange> history) {

  public static TestTicketCloseTaskBuilder ticketCloseTask() {
    return new TestTicketCloseTaskBuilder(TaskStatus.DEPLOYED, List.of());
  }

  public TaskState build() {
    return TaskState.builder("proj", "/wt", status).alias("a1").history(history).build();
  }
}

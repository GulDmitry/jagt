package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestWatchdogTaskBuilder(
    TaskStatus status, @Nullable String message, AgentReport report, long lastActive) {

  public static TestWatchdogTaskBuilder watchedTask() {
    return new TestWatchdogTaskBuilder(TaskStatus.IN_PROGRESS, null, AgentReport.PLAIN, 1_000);
  }

  public TaskState build() {
    return TaskState.builder("proj", "/wt", status)
        .alias("a1")
        .message(message)
        .report(report)
        .lastActiveTimestamp(lastActive)
        .build();
  }
}

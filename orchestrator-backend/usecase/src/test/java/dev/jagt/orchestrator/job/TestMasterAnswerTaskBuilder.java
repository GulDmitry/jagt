package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestMasterAnswerTaskBuilder(
    TaskStatus status,
    @Nullable String message,
    AgentReport report,
    long lastActiveTimestamp,
    @Nullable String repoRequest) {

  public static TestMasterAnswerTaskBuilder askingTask() {
    return new TestMasterAnswerTaskBuilder(
        TaskStatus.IN_PROGRESS, "keep v2?", AgentReport.QUESTION, 0, null);
  }

  public TaskState build() {
    return TaskState.builder("proj", "/wt", status)
        .lastActiveTimestamp(lastActiveTimestamp)
        .message(message)
        .report(report)
        .repoRequest(repoRequest)
        .build();
  }
}

package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestPollingTaskBuilder(
    TaskStatus status,
    @Nullable String mrUrl,
    boolean autoReview,
    long mrCreatedAt,
    long lastPolledAt) {

  public static TestPollingTaskBuilder pollingTask() {
    return new TestPollingTaskBuilder(TaskStatus.CI_POLLING, "http://mr/1", true, 0, 0);
  }

  public TaskState build() {
    return TaskState.builder("proj", "/wt", status)
        .mrUrl(mrUrl)
        .autoReview(autoReview)
        .mrCreatedAt(mrCreatedAt)
        .lastPolledAt(lastPolledAt)
        .build();
  }
}

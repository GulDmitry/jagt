package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestYourMoveTaskBuilder(
    String worktree,
    TaskStatus status,
    @Nullable String message,
    AgentReport report,
    @Nullable String mrUrl,
    @Nullable String repoRequest) {

  public static TestYourMoveTaskBuilder yourMoveTask() {
    return new TestYourMoveTaskBuilder(
        "/wt", TaskStatus.IN_PROGRESS, null, AgentReport.PLAIN, null, null);
  }

  public TaskState build() {
    return TaskState.builder("proj", worktree, status)
        .alias("a1")
        .message(message)
        .report(report)
        .mrUrl(mrUrl)
        .repoRequest(repoRequest)
        .build();
  }
}

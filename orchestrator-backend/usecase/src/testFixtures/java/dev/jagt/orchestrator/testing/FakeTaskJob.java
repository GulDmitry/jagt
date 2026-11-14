package dev.jagt.orchestrator.testing;

import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.task.TaskState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class FakeTaskJob implements TaskJob {

  private final Watch watch;
  private final List<String> handed = new ArrayList<>();

  public FakeTaskJob(Watch watch) {
    this.watch = watch;
  }

  public List<String> handed() {
    return List.copyOf(handed);
  }

  @Override
  public Watch watch() {
    return watch;
  }

  @Override
  public void run(String taskId, TaskState task) {
    handed.add(taskId);
  }

  @Override
  public String id() {
    return "fake-task-job";
  }

  @Override
  public String describe() {
    return "records the ids it is handed";
  }

  @Override
  public @Nullable Duration every() {
    return null;
  }
}

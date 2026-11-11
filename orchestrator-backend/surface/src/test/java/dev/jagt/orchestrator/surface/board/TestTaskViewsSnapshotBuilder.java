package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.service.AutoReviewCadence;
import dev.jagt.orchestrator.service.TaskViews;
import java.time.Duration;
import java.util.List;
import lombok.With;

@With
public record TestTaskViewsSnapshotBuilder(
    List<TaskView> tasks, boolean autoReview, List<String> projects) {

  public static TestTaskViewsSnapshotBuilder taskViewsSnapshot() {
    return new TestTaskViewsSnapshotBuilder(List.of(), false, List.of());
  }

  public TaskViews.Snapshot build() {
    return new TaskViews.Snapshot(
        tasks,
        new AutoReviewCadence(autoReview, Duration.ofHours(24), 10, 60, 240),
        projects,
        null,
        "off");
  }
}

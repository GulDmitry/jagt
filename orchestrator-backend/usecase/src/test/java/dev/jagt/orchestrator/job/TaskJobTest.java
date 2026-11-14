package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.testing.FakeTaskJob;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskJobTest {

  @Test
  void handsTaskJobOnlyTheTasksInTheStatusesItsWatchNames() {
    Map<String, TaskState> tasks = new LinkedHashMap<>();
    tasks.put("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
    tasks.put("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    tasks.put("ABC-3", TaskState.builder("proj", "/wt", TaskStatus.DONE).build());
    FakeTaskJob job = new FakeTaskJob(Watch.defaults().withStatuses(TaskStatus.NEW));

    job.runOn(tasks);

    assertThat(job.handed()).containsExactly("ABC-1");
  }

  @Test
  void handsStoppedTaskOnlyToTaskJobWhoseWatchNamesItsStopKind() {
    FakeTaskJob job = new FakeTaskJob(Watch.defaults().withStops(StopKind.SESSION_ALIVE));
    Map<String, TaskState> tasks = new LinkedHashMap<>();
    tasks.put(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.UNDELIVERED, 1, "evidence"))
            .build());
    tasks.put(
        "ABC-2",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.CHECKS, 1, "evidence"))
            .build());

    job.runOn(tasks);

    assertThat(job.handed()).containsExactly("ABC-1");
  }

  @Test
  void handsPausedTaskToNoTaskJobWhoseWatchNamesEveryOtherStop() {
    FakeTaskJob job = new FakeTaskJob(Watch.defaults().withStops(StopKind.withoutPause()));
    Map<String, TaskState> tasks = new LinkedHashMap<>();
    tasks.put(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.PAUSE, 1, "you paused it"))
            .build());
    tasks.put(
        "ABC-2",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.PACE, 1, "evidence"))
            .build());

    job.runOn(tasks);

    assertThat(job.handed()).containsExactly("ABC-2");
  }
}

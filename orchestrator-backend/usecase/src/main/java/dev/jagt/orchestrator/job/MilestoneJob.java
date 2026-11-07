package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Raises the one event of a task's status turning into {@code CI_POLLING} or {@code DEPLOYED}. A
 * task first seen, a restart included, counts as already there, so a restart tells nobody.
 */
@Service
@RequiredArgsConstructor
public class MilestoneJob implements Job {

  private final StateService stateService;
  private final Notifications notifications;
  private final Map<String, TaskStatus> seen = new ConcurrentHashMap<>();

  @Override
  public String id() {
    return "milestones";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "raise the event of a task's request opening or its deploy landing";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(2);
  }

  @Override
  public void run() {
    Map<String, TaskState> tasks = stateService.tasks();
    seen.keySet().retainAll(tasks.keySet());
    tasks.forEach(this::watch);
  }

  private void watch(String taskId, TaskState task) {
    TaskStatus before = seen.put(taskId, task.status());
    if (before == null || before == task.status()) {
      return;
    }
    switch (task.status()) {
      case CI_POLLING ->
          notifications.send(
              Notification.requestOpened(taskId, String.join("\n", task.requests())));
      case DEPLOYED -> notifications.send(Notification.deployed(taskId, "deployed"));
      default -> {}
    }
  }
}

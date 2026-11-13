package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.flow.Point;
import dev.jagt.orchestrator.flow.StopHold;
import dev.jagt.orchestrator.notify.PointCatalogue.Standing;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.NotificationSink;
import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import dev.jagt.orchestrator.service.InstalledRoles;
import dev.jagt.orchestrator.service.PhaseProfiles;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tells each point a persisted task stands at once per key. What stands when the first read comes,
 * a restart included, is recorded without telling.
 */
@Component
public class PointWatch {

  private final StateService state;
  private final NotificationSink notifications;
  private final InstalledRoles roles;
  private final PhaseProfiles profiles;
  private final AtomicBoolean primed = new AtomicBoolean();

  public PointWatch(
      StateService state,
      NotificationSink notifications,
      InstalledRoles roles,
      PhaseProfiles profiles) {
    this.state = state;
    this.notifications = notifications;
    this.roles = roles;
    this.profiles = profiles;
  }

  @EventListener
  void observe(StateWritten written) {
    scan(written.tasks());
  }

  public void sweep() {
    scan(state.tasks());
  }

  private synchronized void scan(Map<String, TaskState> tasks) {
    boolean silent = !primed.getAndSet(true);
    tasks.forEach((taskId, task) -> tellOnce(taskId, task, silent));
  }

  private void tellOnce(String taskId, TaskState task, boolean silent) {
    StopHold hold = StopHold.of(task, profiles.at(task));
    for (Standing standing : PointCatalogue.of(task, roles.points(), hold)) {
      String id = standing.point().id();
      if (task.toldOrNone().saidPoint(id, standing.key())) {
        continue;
      }
      Point point = standing.point();
      String key = standing.key();
      AtomicBoolean fresh = new AtomicBoolean();
      state.reduce(
          taskId,
          (reducer, current) -> {
            if (current.toldOrNone().saidPoint(id, key)) {
              return current;
            }
            fresh.set(true);
            return reducer.told(current, point, key);
          });
      if (fresh.get() && !silent) {
        notifications.send(Notification.fromAgent(id, taskId, id.replace('-', ' '), key));
      }
    }
  }
}

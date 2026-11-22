package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.notify.Routes.Routed;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.port.channel.Notification;
import dev.jagt.orchestrator.port.channel.NotificationSink;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Tells a point to every channel routed for it, each with its own text. */
@Component
public class Notifications implements NotificationSink {

  private final Routes routes;
  private final Messages messages;
  private final ChannelPosts posts;
  private final TaskStore tasks;

  public Notifications(Routes routes, Messages messages, ChannelPosts posts, TaskStore tasks) {
    this.routes = routes;
    this.messages = messages;
    this.posts = posts;
    this.tasks = tasks;
  }

  @Override
  public void send(Notification sent) {
    Optional<TaskState> task = sent.taskId() == null ? Optional.empty() : tasks.task(sent.taskId());
    Notification notification =
        task.map(taskState -> taskState.ticket().alias()).map(sent::withAlias).orElse(sent);
    for (Routed routed : routes.of(notification.point())) {
      posts.post(routed.channel(), messages.of(routed, notification, task));
    }
  }
}

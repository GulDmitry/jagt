package dev.jagt.orchestrator.notify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.notify.Routes.Routed;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.port.channel.ChannelMessage;
import dev.jagt.orchestrator.port.channel.Notification;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationsTest {

  private static final ChannelAddress DESKTOP =
      new ChannelAddress("desktop", "desktop", "", List.of());
  private static final ChannelAddress TEAM = new ChannelAddress("team", "teams", "HOOK", List.of());

  @Test
  void postsEachRoutedChannelItsOwnMessage() {
    Routes routes = mock(Routes.class);
    Messages messages = mock(Messages.class);
    TaskStore tasks = mock(TaskStore.class);
    Notification deployed = Notification.deployed(Point.DEPLOYED.id(), "ABC-42", "landed");
    Routed onDesktop = new Routed(DESKTOP, "jagt · {title}", "{body}");
    Routed onTeam = new Routed(TEAM, "", "{task} is live");
    ChannelMessage banner =
        new ChannelMessage("deployed", "ABC-42", null, "jagt · deployed", "landed");
    ChannelMessage chat = new ChannelMessage("deployed", "ABC-42", null, "", "ABC-42 is live");
    when(tasks.task("ABC-42")).thenReturn(Optional.empty());
    when(routes.of("deployed")).thenReturn(List.of(onDesktop, onTeam));
    when(messages.of(onDesktop, deployed, Optional.empty())).thenReturn(banner);
    when(messages.of(onTeam, deployed, Optional.empty())).thenReturn(chat);

    ChannelPosts posts = mock(ChannelPosts.class);
    new Notifications(routes, messages, posts, tasks).send(deployed);

    verify(posts).post(DESKTOP, banner);
    verify(posts).post(TEAM, chat);
  }

  @Test
  void postsNothingForPointNoRouteNames() {
    Routes routes = mock(Routes.class);
    ChannelPosts posts = mock(ChannelPosts.class);
    when(routes.of("housekeeping")).thenReturn(List.of());

    new Notifications(routes, mock(Messages.class), posts, mock(TaskStore.class))
        .send(Notification.housekeeping(Point.HOUSEKEEPING.id(), "2 orphans", "see the log"));

    verify(posts, never()).post(any(), any());
  }

  @Test
  void fillsTheMessageWithTheTaskItIsAbout() {
    Routes routes = mock(Routes.class);
    Messages messages = mock(Messages.class);
    TaskStore tasks = mock(TaskStore.class);
    TaskState task =
        TaskState.builder("api", "/wt/ABC-42-api", TaskStatus.IN_PROGRESS).alias("p3").build();
    Routed routed = new Routed(DESKTOP, "{alias}", "{body}");
    Notification stopped =
        Notification.watchdog(Point.STOPPED.id(), "ABC-42", "agent stopped", "silent for 6 min");
    when(tasks.task("ABC-42")).thenReturn(Optional.of(task));
    when(routes.of("stopped")).thenReturn(List.of(routed));

    new Notifications(routes, messages, mock(ChannelPosts.class), tasks).send(stopped);

    verify(messages).of(routed, stopped.withAlias("p3"), Optional.of(task));
  }
}

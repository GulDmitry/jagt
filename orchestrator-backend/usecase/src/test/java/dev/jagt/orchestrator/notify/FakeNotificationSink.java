package dev.jagt.orchestrator.notify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.port.channel.Notification;
import java.util.ArrayList;
import java.util.List;

public final class FakeNotificationSink {

  private final List<Notification> sent = new ArrayList<>();
  private final Notifications notifications = mock(Notifications.class);

  public FakeNotificationSink() {
    doAnswer(call -> sent.add(call.getArgument(0, Notification.class)))
        .when(notifications)
        .send(any());
  }

  public Notifications notifications() {
    return notifications;
  }

  public List<Notification> sent() {
    return List.copyOf(sent);
  }
}

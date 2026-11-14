package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.port.channel.ChannelDriver;
import dev.jagt.orchestrator.port.channel.ChannelMessage;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.service.log.BoardUrl;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** A message about a task links to the board narrowed to that task. */
@Component
public class DesktopNotifier implements ChannelDriver {

  private final UserNotifier os;
  private final BoardUrl boardUrl;

  public DesktopNotifier(UserNotifier os, BoardUrl boardUrl) {
    this.os = os;
    this.boardUrl = boardUrl;
  }

  @Override
  public String kind() {
    return "desktop";
  }

  @Override
  public void deliver(ChannelAddress address, ChannelMessage message) {
    os.notify(message.title(), message.text(), link(message.taskId()));
  }

  private @Nullable String link(@Nullable String taskId) {
    return taskId == null ? null : boardUrl.ofTask(taskId);
  }
}

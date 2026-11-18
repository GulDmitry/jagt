package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.port.channel.ChannelDriver;
import dev.jagt.orchestrator.port.channel.ChannelMessage;
import java.util.ArrayList;
import java.util.List;

public final class FakeChannelDriver implements ChannelDriver {

  public static final String KIND = "fake";

  private final List<ChannelMessage> delivered = new ArrayList<>();

  @Override
  public String kind() {
    return KIND;
  }

  @Override
  public void deliver(ChannelAddress address, ChannelMessage message) {
    delivered.add(message);
  }

  public List<ChannelMessage> delivered() {
    return List.copyOf(delivered);
  }
}

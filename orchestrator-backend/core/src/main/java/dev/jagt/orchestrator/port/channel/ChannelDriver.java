package dev.jagt.orchestrator.port.channel;

import java.util.List;

/**
 * One kind of channel a point can be told through. A driver holds no route and no text: it posts
 * what it is given where the address says, and throws when the post did not land, leaving retry and
 * report to its caller.
 */
public interface ChannelDriver {

  /** The {@code kind} a channel names in {@code jagt.yml}. */
  String kind();

  /** Whether a channel of this kind must name the environment variable holding its secret. */
  default boolean needsSecret() {
    return false;
  }

  /** What is wrong with a channel of this kind as written, one fault per setting at fault. */
  default List<Fault> faults(ChannelAddress address) {
    return List.of();
  }

  /** A setting of the channel, its name under the channel and what is wrong with it. */
  record Fault(String setting, String problem) {}

  void deliver(ChannelAddress address, ChannelMessage message);
}

package dev.jagt.orchestrator.port;

/**
 * One kind of channel a point can be told through. A driver holds no route and no text: it posts
 * what it is given where the address says, and throws when the post did not land, leaving retry and
 * report to its caller.
 */
public interface ChannelDriver {

  /** The {@code kind} a channel names in {@code jagt.yml}. */
  String kind();

  void deliver(ChannelAddress address, ChannelMessage message);
}

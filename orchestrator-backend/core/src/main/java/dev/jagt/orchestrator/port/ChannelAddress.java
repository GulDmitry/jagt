package dev.jagt.orchestrator.port;

import java.util.List;

/**
 * Where a channel reaches: its name in {@code jagt.yml}, its kind, the name of the environment
 * variable holding a secret ({@code ""} when the kind needs none) and the argv of a command (empty
 * when the kind runs none).
 */
public record ChannelAddress(String name, String kind, String secretEnv, List<String> command) {

  public ChannelAddress {
    command = List.copyOf(command);
  }
}

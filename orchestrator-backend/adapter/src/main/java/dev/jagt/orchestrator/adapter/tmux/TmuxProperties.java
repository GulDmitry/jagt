package dev.jagt.orchestrator.adapter.tmux;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** An empty {@code socket} is tmux's default server. */
@ConfigurationProperties(prefix = "orchestrator.tmux")
public record TmuxProperties(String command, String socket) {

  public TmuxProperties {
    command = command == null || command.isBlank() ? "tmux" : command.strip();
    socket = socket == null ? "" : socket.strip();
  }
}

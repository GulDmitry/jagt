package dev.jagt.orchestrator.adapter.terminal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** An empty {@code fontSize} leaves it to kitty's own config. */
@ConfigurationProperties(prefix = "orchestrator.kitty")
public record KittyProperties(String command, String fontSize) {

  public KittyProperties {
    command = command == null || command.isBlank() ? "kitty" : command.strip();
    fontSize = fontSize == null ? "" : fontSize.strip();
  }
}

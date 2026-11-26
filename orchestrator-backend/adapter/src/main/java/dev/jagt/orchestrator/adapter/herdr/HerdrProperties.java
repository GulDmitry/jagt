package dev.jagt.orchestrator.adapter.herdr;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code home} holds herdr's {@code sessions/<name>/herdr.sock}; empty is {@code ~/.config/herdr}.
 * A set one is {@code ~/<path>} or an absolute path with no {@code ..}, ending in {@code herdr}:
 * herdr finds its folder only as {@code $XDG_CONFIG_HOME/herdr}.
 */
@ConfigurationProperties(prefix = "orchestrator.herdr")
public record HerdrProperties(String command, String home) {

  public HerdrProperties {
    command = command == null || command.isBlank() ? "herdr" : command.strip();
    home = home == null ? "" : home.strip();
    if (!home.isEmpty() && !addressable(home)) {
      throw new IllegalArgumentException(
          "orchestrator.herdr.home '"
              + home
              + "' is not addressable: use ~/<path> or an absolute path without '..', ending in"
              + " 'herdr'");
    }
  }

  private static boolean addressable(String home) {
    boolean underHome = home.startsWith("~/");
    Path path = Path.of(underHome ? "/" + home.substring(2) : home);
    return (underHome || path.isAbsolute())
        && path.getParent() != null
        && path.getFileName().toString().equals("herdr")
        && path.normalize().equals(path);
  }
}

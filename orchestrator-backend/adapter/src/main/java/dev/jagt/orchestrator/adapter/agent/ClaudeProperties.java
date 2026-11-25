package dev.jagt.orchestrator.adapter.agent;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * One agent's own settings, under its own key: the generic install config carries no vendor name.
 */
@ConfigurationProperties(prefix = "orchestrator.claude")
public record ClaudeProperties(
    String command, String mcpConfig, String outputStyle, List<String> disabledPlugins) {

  public ClaudeProperties {
    command = command == null || command.isBlank() ? "claude" : command;
    mcpConfig = mcpConfig == null ? "" : mcpConfig.strip();
    outputStyle = outputStyle == null ? "" : outputStyle.strip();
    disabledPlugins = disabledPlugins == null ? List.of() : List.copyOf(disabledPlugins);
  }

  /** Empty when no plugin is disabled; a plugin's servers load only while the plugin is enabled. */
  public List<String> disabledPluginArguments() {
    String entries =
        disabledPlugins.stream()
            .filter(plugin -> !plugin.isBlank())
            .map(plugin -> "\"" + plugin.strip() + "\":false")
            .collect(Collectors.joining(","));
    return entries.isEmpty()
        ? List.of()
        : List.of("--settings", "{\"enabledPlugins\":{" + entries + "}}");
  }

  /** A plugin's server is listed as {@code plugin:<plugin>:<server>}. */
  public boolean disablesServer(String serverName) {
    return disabledPlugins.stream()
        .map(plugin -> plugin.strip().replaceFirst("@.*$", ""))
        .anyMatch(plugin -> !plugin.isEmpty() && serverName.startsWith("plugin:" + plugin + ":"));
  }

  public static ClaudeProperties defaults() {
    return new ClaudeProperties("", "", "", List.of());
  }
}

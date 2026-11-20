package dev.jagt.orchestrator.adapter.agent.trust;

import java.nio.file.Path;
import java.util.Optional;

public final class ClaudeConfigDir {

  public static final String VARIABLE = "CLAUDE_CONFIG_DIR";

  private ClaudeConfigDir() {}

  public static Path of(Optional<String> configured, Path fallback) {
    return configured.filter(dir -> !dir.isBlank()).map(Path::of).orElse(fallback);
  }
}

package dev.jagt.orchestrator.adapter.agent;

import java.nio.file.Path;
import java.util.Optional;

final class ClaudeConfigDir {

  static final String VARIABLE = "CLAUDE_CONFIG_DIR";

  private ClaudeConfigDir() {}

  static Path of(Optional<String> configured, Path fallback) {
    return configured.filter(dir -> !dir.isBlank()).map(Path::of).orElse(fallback);
  }
}

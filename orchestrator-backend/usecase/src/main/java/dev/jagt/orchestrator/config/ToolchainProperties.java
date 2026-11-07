package dev.jagt.orchestrator.config;

import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The external tools' commands, bound from the same {@code orchestrator} keys as {@link
 * OrchestratorProperties}, every value verbatim as written. Build one with {@link #defaults()} plus
 * {@code withX}.
 */
@ConfigurationProperties(prefix = "orchestrator")
@With
public record ToolchainProperties(
    @Nullable List<String> editorCommand,
    @Nullable List<String> editorDiffCommand,
    @Nullable String tmuxCommand,
    @Nullable String tmuxSocket) {

  public static ToolchainProperties defaults() {
    return new ToolchainProperties(null, null, null, null);
  }
}

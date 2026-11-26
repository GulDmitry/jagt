package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.task.text.Blank;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The scripted test runtime's script, given the worktree path and, in plan mode, {@code plan};
 * blank starts and exits.
 */
@ConfigurationProperties(prefix = "orchestrator.stub")
public record StubAgentProperties(@Nullable String script) {

  public StubAgentProperties {
    script = Blank.toNull(script);
  }

  public static StubAgentProperties defaults() {
    return new StubAgentProperties(null);
  }
}

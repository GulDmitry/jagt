package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "orchestrator.session-host",
    havingValue = "tmux",
    matchIfMissing = true)
@RequiredArgsConstructor
public class TmuxCheck implements StartupCheck {

  private final ToolchainProperties properties;

  @Override
  public List<Problem> problems() {
    List<Problem> problems = new ArrayList<>();
    if (Executables.unresolved(Executables.resolve(properties.tmuxCommand()))) {
      problems.add(
          new Problem(
              "orchestrator.tmux-command",
              "orchestrator.tmux-command: '"
                  + properties.tmuxCommand()
                  + "' is not on PATH nor in the usual install directories, and every agent runs"
                  + " inside a tmux session. Install tmux or set the key to a full path."));
    }
    if (!Executables.onPath("pgrep")) {
      problems.add(
          new Problem(
              "pgrep",
              "pgrep is not on PATH — without it every agent's window reads as a dead shell."));
    }
    return problems;
  }
}

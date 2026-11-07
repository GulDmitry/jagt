package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ToolchainCheck implements StartupCheck {

  private final ToolchainProperties properties;

  @Override
  public List<Problem> problems() {
    List<Problem> problems = new ArrayList<>();
    // Spawned by bare name, so an install only the resolver could find is one jagt never calls.
    if (!Executables.onPath("git")) {
      problems.add(
          new Problem(
              "git",
              "git is not on PATH — every worktree, branch and merge jagt makes runs through it."));
    }
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

  @Override
  public List<Problem> notes() {
    return Executables.onPath("lsof")
        ? List.of()
        : List.of(
            new Problem(
                "lsof",
                "lsof is not on PATH — a removed worktree's leftover processes are not stopped."));
  }
}

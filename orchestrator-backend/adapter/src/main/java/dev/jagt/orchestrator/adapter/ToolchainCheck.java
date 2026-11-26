package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ToolchainCheck implements StartupCheck {

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
    if (!Executables.onPath("curl")) {
      problems.add(
          new Problem(
              "curl",
              "curl is not on PATH — every hook jagt installs and every headless read runs through"
                  + " it, so without it each tool call is refused."));
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

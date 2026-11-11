package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.TerminalDriver;
import lombok.With;

@With
public record TestTmuxHostBuilder(ToolchainProperties toolchain) {

  public static TestTmuxHostBuilder tmuxHost() {
    return new TestTmuxHostBuilder(ToolchainProperties.defaults());
  }

  public TmuxSessionHost build(Processes processes, TerminalDriver terminal, AgentRuntime agent) {
    return new TmuxSessionHost(
        processes,
        new TmuxReads(processes, toolchain),
        new OrchestratorPaths(OrchestratorProperties.defaults()),
        terminal,
        agent);
  }

  public TmuxSessionHost build(
      Processes processes, TmuxReads reads, TerminalDriver terminal, AgentRuntime agent) {
    return new TmuxSessionHost(
        processes,
        reads,
        new OrchestratorPaths(OrchestratorProperties.defaults()),
        terminal,
        agent);
  }
}

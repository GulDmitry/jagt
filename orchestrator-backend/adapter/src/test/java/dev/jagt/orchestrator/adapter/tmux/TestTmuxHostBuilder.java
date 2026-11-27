package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.adapter.terminal.SessionEnvironment;
import dev.jagt.orchestrator.adapter.worktree.NoWorktreeContainers;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.testing.FakeHost;
import lombok.With;

@With
public record TestTmuxHostBuilder(TmuxProperties tmux) {

  public static TestTmuxHostBuilder tmuxHost() {
    return new TestTmuxHostBuilder(new TmuxProperties("", ""));
  }

  public TmuxSessionHost build(
      StateService state, Processes processes, TerminalDriver terminal, AgentRuntime agent) {
    return new TmuxSessionHost(
        processes,
        new TmuxReads(processes, tmux),
        new SessionEnvironment(
            new OrchestratorPaths(OrchestratorProperties.defaults(), FakeHost.machine()),
            new NoWorktreeContainers(),
            FakeHost.machine(),
            state),
        terminal,
        agent);
  }

  public TmuxSessionHost build(
      StateService state,
      Processes processes,
      TmuxReads reads,
      TerminalDriver terminal,
      AgentRuntime agent) {
    return new TmuxSessionHost(
        processes,
        reads,
        new SessionEnvironment(
            new OrchestratorPaths(OrchestratorProperties.defaults(), FakeHost.machine()),
            new NoWorktreeContainers(),
            FakeHost.machine(),
            state),
        terminal,
        agent);
  }
}

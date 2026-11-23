package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestAgentWindowsBuilder.agentWindows;
import static dev.jagt.orchestrator.service.TestConfigServiceBuilder.configService;
import static dev.jagt.orchestrator.service.TestFreshStartsBuilder.freshStarts;
import static dev.jagt.orchestrator.service.TestSessionHostingBuilder.sessionHosting;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.session.SessionHosting;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import java.io.IOException;
import java.nio.file.Path;
import lombok.With;

@With
public record TestAgentSessionsBuilder(Path root) {

  public static TestAgentSessionsBuilder agentSessions() {
    return new TestAgentSessionsBuilder(Path.of("/state"));
  }

  public AgentSessions build(
      StateService state, SessionHost host, AgentRuntime runtime, WorktreeChanges changes)
      throws IOException {
    SessionHosting hosting = sessionHosting().build(host, runtime, changes);
    AgentWindows windows =
        agentWindows().build(configService().withRoot(root).build(runtime), state, hosting);
    return new AgentSessions(
        state,
        hosting,
        new FlowRules(new TaskChart(Composition.builtIn())),
        windows,
        freshStarts().build(windows, state, hosting));
  }
}

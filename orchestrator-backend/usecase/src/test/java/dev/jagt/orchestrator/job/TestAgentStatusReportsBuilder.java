package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.HandBack;
import dev.jagt.orchestrator.service.LinkRefusals;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.service.Rounds;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.WorktreeChanges;

public record TestAgentStatusReportsBuilder() {

  public static TestAgentStatusReportsBuilder agentStatusReports() {
    return new TestAgentStatusReportsBuilder();
  }

  public AgentStatusReports build(
      StateService state, FlowReports flow, WorktreeChanges changes, ConfigService config) {
    return new AgentStatusReports(
        state,
        new LinkRefusals(new ReachLedger(), (session, line) -> "", state),
        flow,
        new HandBack(
            changes,
            new Rounds(
                config,
                ignored -> false,
                () -> false,
                () -> false,
                new Checkers(new TaskChart(Composition.builtIn()))),
            config),
        config);
  }
}

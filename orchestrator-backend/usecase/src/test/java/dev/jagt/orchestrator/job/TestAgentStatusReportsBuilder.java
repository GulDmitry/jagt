package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.HandBack;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.service.report.AgentStatusReports;
import dev.jagt.orchestrator.service.review.Rounds;
import dev.jagt.orchestrator.service.session.LinkRefusals;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;

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
                () -> Facts.Reach.defaults(),
                unheld -> null,
                new Checkers(new TaskChart(Composition.builtIn()))),
            config),
        config);
  }
}

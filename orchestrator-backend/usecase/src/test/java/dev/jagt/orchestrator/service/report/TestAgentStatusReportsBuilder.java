package dev.jagt.orchestrator.service.report;

import static dev.jagt.orchestrator.service.report.TestFlowReportsBuilder.flowReports;
import static dev.jagt.orchestrator.service.report.TestHandBackBuilder.handBack;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.session.LinkRefusals;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import lombok.With;

@With
public record TestAgentStatusReportsBuilder() {

  public static TestAgentStatusReportsBuilder agentStatusReports() {
    return new TestAgentStatusReportsBuilder();
  }

  public AgentStatusReports build(
      StateService state,
      LinkRefusals refusals,
      WorktreeChanges worktreeChanges,
      ConfigService configService) {
    return new AgentStatusReports(
        state,
        refusals,
        flowReports().build(state),
        handBack().build(worktreeChanges, configService),
        configService);
  }
}

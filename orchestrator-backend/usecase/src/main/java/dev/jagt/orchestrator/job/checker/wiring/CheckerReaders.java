package dev.jagt.orchestrator.job.checker.wiring;

import dev.jagt.orchestrator.job.checker.CheckerFlows;
import dev.jagt.orchestrator.job.checker.CheckerReads;
import dev.jagt.orchestrator.job.checker.workspace.CheckerWorkspace;
import dev.jagt.orchestrator.job.checker.workspace.WorkspaceRequests;
import dev.jagt.orchestrator.port.JobFaults;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.port.worktree.FlowRunner;
import dev.jagt.orchestrator.service.role.RoleKit;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a checker's read runs on, shared by every checker. */
@Component
@RequiredArgsConstructor
public class CheckerReaders {

  private final FlowRunner runner;
  private final CheckerReader reader;
  private final TaskTurns turns;
  private final WorkspaceRequests requests;
  private final JobFaults faults;

  public CheckerFlows flows(RoleKit kit) {
    return new CheckerFlows(runner, kit.definition(), kit.settings());
  }

  public CheckerReads reads(RoleKit kit) {
    return new CheckerReads(
        reader,
        turns,
        flows(kit),
        new CheckerWorkspace(kit.definition(), kit.settings(), requests),
        faults);
  }
}

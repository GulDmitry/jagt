package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.ConflictEnding;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Ends a red-pipeline conflict on a later green pipeline on the deploy commit's branch. */
@Component
@RequiredArgsConstructor
class LaterGreenRead implements ConflictRead {

  private final ConfigService configService;
  private final DeployPipelines pipelines;
  private final FlowReports reports;

  @Override
  public ConflictEnding ending() {
    return ConflictEnding.LATER_GREEN;
  }

  @Override
  public boolean read(String taskId, TaskState task) {
    if (!pipelines.redConflictDisproved(task)) {
      return false;
    }
    String branches = DeployTargets.deployBranches(task, configService.load().projects());
    reports.endRedConflict(taskId, "deployed to " + branches);
    return true;
  }
}

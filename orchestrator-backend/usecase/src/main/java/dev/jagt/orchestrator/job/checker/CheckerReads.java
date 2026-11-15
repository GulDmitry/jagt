package dev.jagt.orchestrator.job.checker;

import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.AcceptanceDecline;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import lombok.RequiredArgsConstructor;

/**
 * An acceptance read held as a turn: open while the run reads, closed before what it found is acted
 * on.
 */
@RequiredArgsConstructor
public class CheckerReads {

  private final RoleDefinition role;
  private final CheckerReader reader;
  private final TaskTurns turns;
  private final CheckerFlows flows;

  public Answer<AcceptanceVerdict> check(
      String taskId, String step, String digest, CheckerReader.Run run) {
    turns.opened(taskId, step, digest, TaskStatus.DEPLOYED);
    try {
      return reader.read(run).map(verdict -> flows.run(verdict, run.worktree(), verdict.flows()));
    } finally {
      turns.closed(taskId, role.id(), step, digest);
    }
  }

  public Answer<AcceptanceDecline> decline(
      String taskId, String step, String digest, CheckerReader.Run run) {
    turns.opened(taskId, step, digest, TaskStatus.DEPLOYED);
    try {
      return reader.reconsider(run);
    } finally {
      turns.closed(taskId, role.id(), step, digest);
    }
  }
}

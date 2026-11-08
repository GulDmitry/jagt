package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A QA read held as a turn: open while the run reads, closed before what it found is acted on. */
@Component
@RequiredArgsConstructor
public class CheckerReads {

  private final RoleDefinition role;
  private final CheckerReader reader;
  private final TaskTurns turns;
  private final CheckerFlows flows;

  public Answer<QaVerdict> check(String taskId, String step, String digest, CheckerReader.Run run) {
    turns.opened(taskId, step, digest, TaskStatus.DEPLOYED);
    try {
      return reader
          .read(run)
          .map(verdict -> flows.run(verdict, run.worktree(), verdict.flows()));
    } finally {
      turns.closed(taskId, role.id(), step, digest);
    }
  }

  public Answer<QaDecline> decline(
      String taskId, String step, String digest, CheckerReader.Run run) {
    turns.opened(taskId, step, digest, TaskStatus.DEPLOYED);
    try {
      return reader.reconsider(run);
    } finally {
      turns.closed(taskId, role.id(), step, digest);
    }
  }
}

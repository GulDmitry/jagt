package dev.jagt.orchestrator.job.checker;

import dev.jagt.orchestrator.job.checker.workspace.CheckerWorkspace;
import dev.jagt.orchestrator.port.JobFaults;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.protocol.read.RetryPolicy;
import dev.jagt.orchestrator.protocol.read.RowStatusRead;
import dev.jagt.orchestrator.service.assistant.PaidRead;
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

  private final CheckerReader reader;
  private final TaskTurns turns;
  private final CheckerFlows flows;
  private final CheckerWorkspace workspace;
  private final JobFaults faults;

  public Answer<AcceptanceVerdict> check(
      String taskId, String step, String digest, CheckerReader.Run run) {
    turns.opened(taskId, step, digest, TaskStatus.DEPLOYED);
    try {
      Answer<AcceptanceVerdict> answer =
          PaidRead.untilWithoutViolations(
                  RetryPolicy.ROUND_READ,
                  run.role(),
                  corrections -> reader.read(run.correcting(corrections)),
                  RowStatusRead::violations)
              .map(RowStatusRead::closed)
              .map(verdict -> flows.run(verdict, run.worktree(), verdict.flows()));
      if (answer.facts().isPresent()) {
        workspace.offer(taskId);
      }
      return reported(answer);
    } finally {
      turns.closed(taskId, run.role(), step, digest);
    }
  }

  public Answer<AcceptanceDecline> decline(
      String taskId, String step, String digest, CheckerReader.Run run) {
    turns.opened(taskId, step, digest, TaskStatus.DEPLOYED);
    try {
      return reported(reader.reconsider(run));
    } finally {
      turns.closed(taskId, run.role(), step, digest);
    }
  }

  private <T> Answer<T> reported(Answer<T> answer) {
    if (answer.facts().isEmpty() && !answer.failure().isBlank()) {
      faults.faulted(answer.failure());
    }
    return answer;
  }
}

package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.TicketTexts;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.service.WorktreeFiles;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskState;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What jagt reads for a round once, so the roles judging it quote it rather than each read it
 * again.
 */
@Component
@RequiredArgsConstructor
public class RoundFacts {

  private final TicketTexts tickets;
  private final WorktreeChanges changes;
  private final AgentRuntime agentRuntime;

  /**
   * The ticket as read, else what the task was opened with: a task nobody filed has no other ask.
   */
  public String ask(String taskId, TaskState task) {
    return ticket(taskId).orElseGet(() -> request(task));
  }

  /** Empty until a read of the ticket came back, and again after a restart. */
  public Optional<String> ticket(String taskId) {
    return tickets.of(taskId);
  }

  /** What the task was opened with. */
  public String request(TaskState task) {
    return WorktreeFiles.read(Path.of(task.worktreePath()).resolve(Artifact.REQUEST.fileName()))
        .orElse("")
        .strip();
  }

  public String diff(TaskState task) {
    return changes.diff(task);
  }

  /** Every repository's changed paths; empty where git could not answer for one. */
  public Optional<List<String>> paths(TaskState task) {
    return changes.paths(task).map(paths -> paths.values().stream().flatMap(List::stream).toList());
  }

  public Optional<String> ownDiff(TaskState task) {
    return changes.ownDiff(task);
  }

  /** Blank where the session left none. */
  public String notes(TaskState task) {
    return WorktreeFiles.read(Path.of(task.worktreePath()).resolve(Artifact.NOTES.fileName()))
        .orElse("")
        .strip();
  }

  /** Blank where the session wrote none. */
  public String plan(TaskState task) {
    return WorktreeFiles.read(Path.of(task.worktreePath()).resolve(Artifact.PLAN.fileName()))
        .orElse("")
        .strip();
  }

  /** Empty where the session's record could not be read. */
  public Optional<List<String>> humanSaid(TaskState task) {
    return agentRuntime.humanSaid(Path.of(task.worktreePath()), Set.of(AgentSessions.NUDGE));
  }
}

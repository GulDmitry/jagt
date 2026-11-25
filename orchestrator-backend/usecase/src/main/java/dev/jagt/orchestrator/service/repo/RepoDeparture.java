package dev.jagt.orchestrator.service.repo;

import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.worktree.RetiredWorktrees;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * What a repository leaving a task leaves behind: its checkout goes, its branch stays, the session
 * is told.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RepoDeparture {

  private final ConfigService configService;
  private final RetiredWorktrees worktrees;
  private final AgentSessions sessions;
  private final StateService stateService;
  private final Clock clock;

  /**
   * Takes {@code repo} out of the task and settles what it leaves behind. The task's primary
   * repository and one whose change is deployed stay, and this answers false.
   */
  public boolean leave(String taskId, TaskRepo repo, String cause) {
    return depart(taskId, repo, cause, false);
  }

  /** As {@link #leave}, the primary included once no agent runs in its checkout. */
  public boolean leaveWorkless(String taskId, TaskRepo repo, String cause) {
    return depart(taskId, repo, cause, true);
  }

  private boolean depart(String taskId, TaskRepo repo, String cause, boolean primaryMayGo) {
    String project = Objects.requireNonNull(repo.project());
    TaskState task = stateService.task(taskId).orElse(null);
    if (task == null || !task.canLose(project) || task.work().landedIn(project)) {
      return false;
    }
    boolean primary = task.isPrimary(project);
    if (primary && (!primaryMayGo || agentRuns(taskId))) {
      return false;
    }
    Set<String> trustedOnlyByIt = worktrees.trustedOnlyBy(taskId, task, repo);
    stateService.reduce(
        taskId,
        (reducer, current) -> reducer.repoLeft(current, project, trustedOnlyByIt, clock.millis()));
    settle(taskId, repo, cause, !primary);
    return true;
  }

  private boolean agentRuns(String taskId) {
    try {
      return sessions.windowState(taskId) == SessionHost.WindowState.AGENT_RUNNING;
    } catch (RuntimeException unknown) {
      return true;
    }
  }

  private void settle(String taskId, TaskRepo repo, String cause, boolean tellSession) {
    log.atInfo()
        .setMessage("repository left the task")
        .addKeyValue("task", taskId)
        .addKeyValue("project", repo.project())
        .addKeyValue("request", repo.mrUrl())
        .addKeyValue("cause", cause)
        .log();
    try {
      worktrees.remove(taskId, List.of(repo), configService.load().projects());
    } catch (RuntimeException failed) {
      log.atWarn()
          .setMessage("checkout of repository not removed")
          .addKeyValue("task", taskId)
          .addKeyValue("project", repo.project())
          .addKeyValue("cause", failed.toString())
          .addKeyValue("effect", "the repository is out of the task; its checkout stays on disk")
          .log();
    }
    if (tellSession) {
      tell(taskId, repo, cause);
    }
  }

  private void tell(String taskId, TaskRepo repo, String cause) {
    try {
      sessions.relayUnder(
          taskId,
          "jagt took "
              + repo.project()
              + " out of this task: "
              + cause
              + ". Its branch "
              + taskId
              + " is kept; do not work there.",
          false);
    } catch (RuntimeException unheard) {
      log.atWarn()
          .setMessage("session not told of repository leaving")
          .addKeyValue("task", taskId)
          .addKeyValue("project", repo.project())
          .addKeyValue("cause", unheard.toString())
          .addKeyValue(
              "effect",
              "the repository is out of the task; the session learns it from its next brief")
          .log();
    }
  }
}

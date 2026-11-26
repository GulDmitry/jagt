package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.service.session.SessionHosting;
import dev.jagt.orchestrator.service.worktree.WorktreeFiles;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.plan.Artifact;
import dev.jagt.orchestrator.task.usage.ModelTier;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Starting a session over with the old conversation carried, waiting while a human holds the
 * window.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FreshStarts {

  private final AgentWindows windows;
  private final StateService stateService;
  private final SessionHosting hosting;

  private final ConcurrentHashMap<String, FreshWait> freshWaits = new ConcurrentHashMap<>();

  boolean startWhenFree(String taskId, TaskState task, String cause) {
    if (!humanHolds(windows.session(taskId), taskId)) {
      startFresh(taskId, task, cause, null);
      return true;
    }
    waits(taskId, task.ticket().alias(), cause);
    return false;
  }

  /** An unanswered question of the agent: a fresh window would close it. */
  boolean questionOpen(String taskId) {
    String session = windows.session(taskId);
    return windows.running(session, taskId) && windows.promptOpen(session, taskId);
  }

  /** A window that is not running, or a task that is gone, holds nobody. */
  private boolean humanHolds(String session, String taskId) {
    return windows.running(session, taskId)
        && stateService.task(taskId).isPresent()
        && (windows.composing(session, taskId)
            || windows.promptOpen(session, taskId)
            || humanTurnOpen(taskId));
  }

  private boolean humanTurnOpen(String taskId) {
    return stateService
        .task(taskId)
        .filter(task ->
            hosting
                .runtime()
                .humanExchangeOpen(Path.of(task.worktreePath()), Set.of(AgentWindows.NUDGE)))
        .isPresent();
  }

  /**
   * The instructions are on disk already; what ends the wait is the human leaving, the window dying
   * or the task going.
   */
  private void waits(String taskId, @Nullable String alias, String cause) {
    if (freshWaits.putIfAbsent(taskId, new FreshWait(alias, cause, false)) != null) {
      return;
    }
    log.atInfo()
        .setMessage("fresh start waits")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", alias)
        .addKeyValue(
            "cause", "the human has unsent input or an open exchange in the agent's window")
        .addKeyValue("start", cause)
        .log();
  }

  /**
   * One read of a standing wait: the human must be seen gone twice running before the fresh session
   * starts.
   */
  void startWaitingWhenFree(String taskId) {
    FreshWait wait = freshWaits.get(taskId);
    if (wait == null) {
      return;
    }
    Optional<TaskState> task = stateService.task(taskId);
    if (task.isEmpty()) {
      freshWaits.remove(taskId, wait);
      log.atInfo()
          .setMessage("fresh start dropped")
          .addKeyValue("task", taskId)
          .addKeyValue("alias", wait.alias())
          .addKeyValue("cause", "the task is gone")
          .log();
      return;
    }
    Optional<Boolean> holds = humanHoldsRead(windows.session(taskId), taskId);
    if (holds.isEmpty()) {
      return;
    }
    if (holds.get() || !wait.free()) {
      freshWaits.replace(taskId, wait, wait.seen(!holds.get()));
    } else if (freshWaits.remove(taskId, wait)) {
      log.atInfo()
          .setMessage("fresh start proceeds")
          .addKeyValue("task", taskId)
          .addKeyValue("alias", wait.alias())
          .addKeyValue("cause", "the human left the agent's window")
          .log();
      startFresh(taskId, task.get(), wait.cause(), null);
    }
  }

  private Optional<Boolean> humanHoldsRead(String session, String taskId) {
    try {
      return Optional.of(humanHolds(session, taskId));
    } catch (RuntimeException unread) {
      log.atDebug()
          .setMessage("fresh start wait unread")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", unread.toString())
          .log();
      return Optional.empty();
    }
  }

  private record FreshWait(@Nullable String alias, String cause, boolean free) {
    FreshWait seen(boolean nowFree) {
      return new FreshWait(alias, cause, nowFree);
    }
  }

  void startFresh(String taskId, TaskState task, String cause, @Nullable ModelTier at) {
    log.atInfo()
        .setMessage("agent session started fresh")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("cause", cause)
        .log();
    Path contextFile = Path.of(task.worktreePath()).resolve(Artifact.CONTEXT.fileName());
    synchronized (windows.contextLock(taskId)) {
      WorktreeFiles.read(contextFile)
          .ifPresent(held -> WorktreeFiles.write(contextFile, hosting.carried().after(held, task)));
    }
    windows.openTab(
        taskId, task.ticket().alias(), Path.of(task.worktreePath()), LaunchMode.WORK, at);
  }
}

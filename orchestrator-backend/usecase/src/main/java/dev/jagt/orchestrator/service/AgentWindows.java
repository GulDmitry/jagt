package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.session.SessionHosting;
import dev.jagt.orchestrator.service.worktree.WorktreeFiles;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.launch.TaskName;
import dev.jagt.orchestrator.task.plan.Artifact;
import dev.jagt.orchestrator.task.usage.ModelTier;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * The window an agent session lives in: its name, what it shows, what is typed into it, its tab.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentWindows {

  private static final int RESUBMITS = 3;

  /** The one line jagt types into a session; whatever else arrives there is the human's. */
  public static final String NUDGE =
      "task_context.md changed — re-read it now and follow the new instructions.";

  private final ConfigService configService;
  private final StateService stateService;
  private final SessionHosting hosting;

  /**
   * Never pruned: a lock dropped while a writer holds it lets a second writer in, and an entry
   * costs bytes.
   */
  private final ConcurrentHashMap<String, Object> contextLocks = new ConcurrentHashMap<>();

  /**
   * One monitor per task id, so writes of one task's context file serialise while different tasks
   * never wait on each other.
   */
  Object contextLock(String taskId) {
    return contextLocks.computeIfAbsent(taskId, id -> new Object());
  }

  String session(String taskId) {
    return sessionIn(configService.load(), taskId);
  }

  String viewerTitle() {
    return hosting.host().sessionName(configService.load().viewer().session());
  }

  /** Reserving the viewer is the default: one placed by hand should survive task cycles. */
  Optional<String> viewerToClose() {
    ConfigFile config = configService.load();
    if (!stateService.tasks().isEmpty() || config.viewer().keepViewerOrDefault()) {
      return Optional.empty();
    }
    return Optional.of(hosting.host().sessionName(config.viewer().session()));
  }

  Duration draftPoll() {
    return configService.load().agent().draftPollOrDefault();
  }

  boolean cold(TaskState task) {
    return SessionPhase.cold(
        task.work().session(),
        hosting.runtime(),
        configService.load().agent().cacheLifetimeOrDefault());
  }

  boolean running(String session, String taskId) {
    return hosting.host().taskWindowState(session, taskId) == SessionHost.WindowState.AGENT_RUNNING;
  }

  /** A screen that cannot be read is no screen. */
  Optional<String> shown(String session, String taskId) {
    try {
      return hosting.host().screenOf(session, taskId);
    } catch (RuntimeException unread) {
      log.atDebug()
          .setMessage("session screen unread")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", unread.toString())
          .log();
      return Optional.empty();
    }
  }

  /**
   * A screen that cannot be read holds no draft we know of, so the nudge goes ahead as for an empty
   * prompt.
   */
  boolean composing(String session, String taskId) {
    return shown(session, taskId).filter(hosting.runtime()::holdsDraft).isPresent();
  }

  boolean promptOpen(String session, String taskId) {
    return shown(session, taskId).flatMap(hosting.runtime()::unansweredPrompt).isPresent();
  }

  /**
   * A wrapped input shows the nudge's first row alone, which must still be most of it to be ours.
   */
  static boolean isNudge(String draft) {
    return NUDGE.startsWith(draft) && draft.length() >= NUDGE.length() / 2;
  }

  /**
   * An Enter lost to a turn ending as it landed leaves the line in the input, so it is pressed
   * again.
   */
  Optional<String> undelivered(String session, String taskId, String line) {
    Duration submitWait = configService.load().agent().submitWaitOrDefault();
    try {
      for (int pressed = 0; pressed <= RESUBMITS; pressed++) {
        Thread.sleep(submitWait);
        if (!stillTyped(session, taskId, line)) {
          return Optional.empty();
        }
        if (pressed < RESUBMITS && !hosting.host().submitTaskWindow(session, taskId)) {
          return Optional.of("its window closed before the line left its input");
        }
      }
    } catch (InterruptedException stopped) {
      Thread.currentThread().interrupt();
      return Optional.of("jagt stopped before the line was seen to leave the input");
    }
    String cause =
        "the line still sat unsent in the agent's input after Enter was pressed "
            + (RESUBMITS + 1)
            + " times";
    log.atWarn()
        .setMessage("nudge unsent")
        .addKeyValue("task", taskId)
        .addKeyValue("cause", cause)
        .log();
    return Optional.of(cause);
  }

  /** A screen that cannot be read shows no line of ours, so nothing is pressed blind. */
  private boolean stillTyped(String session, String taskId, String line) {
    return shown(session, taskId)
        .flatMap(hosting.runtime()::draft)
        .filter(line.strip()::startsWith)
        .isPresent();
  }

  /** {@code at} is the tier a stop moved the session to; without one it starts on its own model. */
  String openTab(
      String taskId,
      @Nullable String alias,
      Path worktreePath,
      boolean planMode,
      @Nullable ModelTier at) {
    ConfigFile config = configService.load();
    String own = modelFor(worktreePath, config);
    ModelTier tier =
        at == null ? ModelTier.above(own, config.agent().tiersFor(hosting.runtime())) : at;
    stateService.reduce(taskId, (reducer, task) -> reducer.sessionStartedOver(task).withTier(tier));
    String session = sessionIn(config, taskId);
    hosting
        .host()
        .openTaskWindow(
            session,
            hosting.host().sessionName(config.viewer().session()),
            taskId,
            alias,
            worktreePath,
            planMode,
            tier == null ? own : tier.current());
    return session;
  }

  /** The notes' `next-model:` wins over `agent.model`; blank is the CLI's own default. */
  private String modelFor(Path worktree, ConfigFile config) {
    List<String> models = config.agent().modelsFor(hosting.runtime());
    return WorktreeFiles.read(worktree.resolve(Artifact.NOTES.fileName()))
        .flatMap(TaskNotes::nextModel)
        .or(() -> Optional.of(config.agent().modelOrInherited()))
        .filter(models::contains)
        .orElse("");
  }

  private String sessionIn(ConfigFile config, String taskId) {
    String base = hosting.host().sessionName(config.viewer().session());
    return config.viewer().sharedView() ? base : base + "-" + TaskName.slug(taskId);
  }
}

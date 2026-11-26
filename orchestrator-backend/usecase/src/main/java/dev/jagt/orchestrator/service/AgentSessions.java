package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.session.SessionHosting;
import dev.jagt.orchestrator.service.session.SessionLines;
import dev.jagt.orchestrator.service.session.SessionWindow;
import dev.jagt.orchestrator.service.worktree.FileStamps;
import dev.jagt.orchestrator.service.worktree.WorktreeFiles;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.plan.Artifact;
import dev.jagt.orchestrator.task.text.Ansi;
import dev.jagt.orchestrator.task.usage.ModelTier;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Which agent runs here is never assumed: window titles, liveness and the words in these messages
 * all come from the runtime.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSessions implements SessionLines {

  private final StateService stateService;
  private final SessionHosting hosting;
  private final FlowRules rules;
  private final AgentWindows windows;
  private final FreshStarts freshStarts;

  private final Set<String> draftWaits = ConcurrentHashMap.newKeySet();

  public void startAgent(
      String taskId, @Nullable String alias, Path worktreePath, LaunchMode mode) {
    windows.openTab(taskId, alias, worktreePath, mode, null);
  }

  /**
   * What provisioning wrote outside the worktree is the runtime's to undo, and only it knows what
   * that was.
   */
  public void forgetWorktree(Path worktreePath) {
    hosting.runtime().retireWorktree(worktreePath);
  }

  public void killWindows(String taskId) {
    hosting.host().killTaskWindows(windows.session(taskId), taskId);
  }

  public Optional<String> viewerToClose() {
    return windows.viewerToClose();
  }

  /** A session that cannot list its windows throws: unknown is never missing. */
  public SessionHost.WindowState windowState(String taskId) {
    return hosting.host().taskWindowState(windows.session(taskId), taskId);
  }

  /** When the task's window last printed, epoch millis, 0 where it never did. */
  public long printedAt(String taskId) {
    return hosting.host().lastWindowActivityMillis(windows.session(taskId), taskId);
  }

  private TaskState requireTask(String taskId) {
    return stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
  }

  /** The window a task's session lives in, whether or not it is open now. */
  public SessionWindow windowOf(String taskId) {
    String id = stateService.canonicalTaskId(taskId);
    requireTask(id);
    return new SessionWindow(windows.session(id), id);
  }

  public String openTaskTab(String taskId, @Nullable String mode) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    LaunchMode launch = launchMode(mode);
    if (launch == LaunchMode.PLAN && !rules.planMayComeFirst(task.standing().status())) {
      throw Refusal.byState(
          "Task "
              + taskId
              + " is "
              + task.standing().status()
              + ", so it cannot open in plan mode: a plan is what comes before the code, not after"
              + " it");
    }
    stateService.reduce(
        taskId, (reducer, was) -> reducer.planAsked(was, launch == LaunchMode.PLAN));
    String session =
        windows.openTab(taskId, task.ticket().alias(), Path.of(task.worktreePath()), launch, null);
    return "New "
        + hosting.runtime().displayName()
        + " session started for "
        + taskId
        + " in tab '"
        + taskId
        + "' of session '"
        + session
        + "' (worktree "
        + task.worktreePath()
        + ")"
        + (launch == LaunchMode.PLAN
            ? "; it plans first, and the plan is approved on the board with approve-plan"
            : "");
  }

  /** The worktree and the state entry stay. */
  public String closeTaskTab(String taskId) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    int killed = hosting.host().killTaskWindows(windows.session(taskId), taskId);
    return killed == 0
        ? "No tab named '" + taskId + "' found — the session was already closed."
        : "Closed "
            + killed
            + " tab(s) for "
            + taskId
            + "; the "
            + hosting.runtime().displayName()
            + " session is terminated. Worktree kept: "
            + task.worktreePath();
  }

  /**
   * What the task's session shows now, colour codes removed; empty when no window holds it. Changes
   * nothing.
   */
  public Optional<String> screen(String taskId) {
    String id = stateService.canonicalTaskId(taskId);
    requireTask(id);
    return hosting
        .host()
        .screenOf(windows.session(id), id)
        .map(shown -> Ansi.strip(shown).stripTrailing());
  }

  /**
   * What the task's agent printed last before it exited; empty while it runs or once its window is
   * gone.
   */
  public Optional<String> exitTail(String taskId) {
    return hosting.host().exitTail(windows.session(taskId), taskId);
  }

  /**
   * Why the task's session waits at a prompt jagt never answers; empty where its window shows none.
   * Reads only.
   */
  public Optional<String> unansweredPrompt(String taskId) {
    return hosting
        .host()
        .screenOf(windows.session(taskId), taskId)
        .flatMap(hosting.runtime()::unansweredPrompt);
  }

  /** A session that is gone or dead is re-entered first: focus must always land on a live agent. */
  public Focused focusTask(String taskId) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    String session = windows.session(taskId);
    String dedicatedTitle = windows.viewerTitle();
    boolean revived;
    try {
      revived = !windows.running(session, taskId);
      if (revived) {
        hosting
            .host()
            .reviveTaskWindow(
                session,
                dedicatedTitle,
                taskId,
                task.ticket().alias(),
                Path.of(task.worktreePath()));
      }
      hosting.host().focusTaskWindow(session, dedicatedTitle, taskId);
    } catch (SessionHost.WindowsUnreadable unread) {
      throw Refusal.byState("Nothing was focused: " + unread.getMessage());
    }
    return new Focused(taskId, dedicatedTitle, revived ? hosting.runtime().displayName() : null);
  }

  /**
   * {@code revivedAs} names the agent re-entered because the session was down; null where it ran.
   */
  public record Focused(String taskId, String viewerTitle, @Nullable String revivedAs) {}

  public AgentRuntime.Admission admit(String taskId, Path directory) {
    return hosting
        .runtime()
        .admit(
            Path.of(requireTask(stateService.canonicalTaskId(taskId)).worktreePath()), directory);
  }

  public String writeTaskContext(String taskId, String instructions) {
    return relay(taskId, instructions, null);
  }

  /**
   * A line the human types, typed into the running session as they would have typed it in its
   * window. Never a relay: that OVERWRITES task_context.md, and the round's brief is still what the
   * agent is working from.
   */
  @Override
  public String say(String taskId, String line) {
    String id = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(id);
    String session = windows.session(id);
    if (windows.cold(task) && !windows.promptOpen(session, id)) {
      return under(id, line, COLD_PROMPT);
    }
    typed(session, id, line);
    log.atInfo()
        .setMessage("line said to agent")
        .addKeyValue("task", id)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("said", OneLine.clipped(line))
        .log();
    return "Said to the agent.";
  }

  /** A cold session is typed into too: a shell line never reaches the model. */
  public String run(String taskId, String line) {
    String id = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(id);
    String escape =
        hosting
            .runtime()
            .shellEscape()
            .orElseThrow(() ->
                Refusal.byState(
                    hosting.runtime().displayName() + " has no shell escape to run a line"));
    typed(windows.session(id), id, escape + line);
    log.atInfo()
        .setMessage("line run in agent")
        .addKeyValue("task", id)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("chars", line.length())
        .log();
    return "Run in the agent's window.";
  }

  private void typed(String session, String id, String text) {
    if (!windows.running(session, id) || !hosting.host().nudgeTaskWindow(session, id, text)) {
      throw new IllegalStateException(
          "No "
              + hosting.runtime().displayName()
              + " session is running for "
              + id
              + " — restart the agent, then say it again.");
    }
    windows
        .undelivered(session, id, text)
        .ifPresent(cause -> {
          throw new IllegalStateException(
              "The line was typed into " + id + "'s window, but " + cause + ".");
        });
  }

  public String press(String taskId, SessionHost.Key key) {
    String id = stateService.canonicalTaskId(taskId);
    requireTask(id);
    String session = windows.session(id);
    if (!windows.running(session, id) || !hosting.host().pressTaskWindow(session, id, key)) {
      throw new IllegalStateException(
          "No "
              + hosting.runtime().displayName()
              + " session is running for "
              + id
              + " — restart the agent, then press the key again.");
    }
    return "Pressed " + key + ".";
  }

  /** Presses the runtime's interrupt where its turn is running; false where nothing ran. */
  public boolean interrupt(String taskId) {
    String id = stateService.canonicalTaskId(taskId);
    String session = windows.session(id);
    if (!windows.running(session, id)
        || !windows.shown(session, id).filter(hosting.runtime()::working).isPresent()) {
      return false;
    }
    return hosting.host().pressTaskWindow(session, id, SessionHost.Key.ESCAPE);
  }

  /**
   * Work on a round already handed back needs the notes and the brief, not the conversation that
   * wrote them.
   */
  public String relayFresh(String taskId, String instructions) {
    return relay(taskId, instructions, ROUND_HANDED_BACK);
  }

  /**
   * Keeps what the context file holds above its last addition, so an instruction still pending is
   * not lost.
   */
  public String relayUnder(String taskId, String addition) {
    return under(taskId, addition, null);
  }

  /** The same, on a fresh session: the round was already handed back. */
  public String relayUnderFresh(String taskId, String addition) {
    return under(taskId, addition, ROUND_HANDED_BACK);
  }

  /**
   * The human's own prompt, cold: a fresh session starts at once. False, with nothing written,
   * where an open question of the old session would be lost.
   */
  public boolean relayUnderCold(String taskId, String addition) {
    String id = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(id);
    if (freshStarts.questionOpen(id)) {
      return false;
    }
    write(id, task, instructionsUnder(id, task, addition));
    freshStarts.startFresh(id, task, COLD_PROMPT, null);
    return true;
  }

  private String under(String taskId, String addition, @Nullable String freshBecause) {
    String id = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(id);
    return relay(id, instructionsUnder(id, task, addition), freshBecause);
  }

  private String instructionsUnder(String id, TaskState task, String addition) {
    Path contextFile = Path.of(task.worktreePath()).resolve(Artifact.CONTEXT.fileName());
    synchronized (windows.contextLock(id)) {
      String standing =
          WorktreeFiles.read(contextFile)
              .map(CarriedBlock::without)
              .map(held -> held.contains(UNDER) ? held.substring(0, held.indexOf(UNDER)) : held)
              .orElse("");
      return standing.isBlank() ? addition : standing + UNDER + addition;
    }
  }

  private static final String UNDER =
      "\n\n--- added by jagt; the instruction above still stands ---\n\n";

  private static final String ROUND_HANDED_BACK = "the round was already handed back";

  private static final String COLD_PROMPT = "a prompt typed after the cache lifetime";

  private static final String IDLE_PAST_CACHE = "conversation idle past its cache lifetime";

  private String relay(String taskId, String instructions, @Nullable String freshBecause) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    write(taskId, task, instructions);
    // A file on disk doesn't wake a running agent session — nudge it directly.
    String session = windows.session(taskId);
    if (freshBecause != null || windows.cold(task)) {
      return relayFreshStart(taskId, task, freshBecause);
    }
    if (windows.running(session, taskId)) {
      Optional<String> screen = windows.shown(session, taskId);
      if (screen.filter(hosting.runtime()::holdsDraft).isPresent()) {
        if (resubmittedOwnNudge(screen.get(), session, taskId, task.ticket().alias())) {
          return "Instructions written to task_context.md; the nudge already sat unsent in the"
              + " agent's window, so it was sent.";
        }
        nudgeOnceSent(session, taskId, task.ticket().alias());
        return "Instructions written to task_context.md; the human is typing in the agent's window,"
            + " so the nudge waits until they send it.";
      }
      if (hosting.host().nudgeTaskWindow(session, taskId, AgentWindows.NUDGE)) {
        return windows
            .undelivered(session, taskId, AgentWindows.NUDGE)
            .map(cause ->
                "Instructions written to task_context.md, but the nudge failed: " + cause + ".")
            .orElse(
                "Instructions written to task_context.md and the agent was nudged to re-read"
                    + " them.");
      }
    }
    log.atInfo()
        .setMessage("agent session re-entered")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("cause", "session was down when instructions were relayed")
        .log();
    // A re-entered session reads task_context.md again, so a relay cannot dead-end against a dead
    // agent.
    hosting
        .host()
        .reviveTaskWindow(
            session,
            windows.viewerTitle(),
            taskId,
            task.ticket().alias(),
            Path.of(task.worktreePath()));
    return "Instructions written to task_context.md; the agent session was down, so it was"
        + " re-entered to read and follow them.";
  }

  private void write(String taskId, TaskState task, String instructions) {
    Path contextFile = Path.of(task.worktreePath()).resolve(Artifact.CONTEXT.fileName());
    // One relay at a time per task: interleaving two writes to one file loses an instruction.
    synchronized (windows.contextLock(taskId)) {
      WorktreeFiles.write(contextFile, instructions);
    }
    // The opening line only: a brief runs to hundreds of lines.
    log.atInfo()
        .setMessage("instructions relayed")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("chars", instructions.length())
        .addKeyValue("said", OneLine.clipped(instructions.lines().findFirst().orElse("(empty)")))
        .log();
  }

  private String relayFreshStart(String taskId, TaskState task, @Nullable String freshBecause) {
    boolean started =
        freshStarts.startWhenFree(
            taskId, task, freshBecause != null ? freshBecause : IDLE_PAST_CACHE);
    return started
        ? "Instructions written to task_context.md; a fresh session was started to read them with"
            + " task_notes.md."
        : "Instructions written to task_context.md; the human is in the agent's window, so the"
            + " fresh session starts when they are done.";
  }

  /** Whether the human has an unsent draft in the task's window. */
  public boolean composing(String taskId) {
    return windows.composing(windows.session(taskId), taskId);
  }

  /** Our own nudge left unsent is no human's draft: Enter is pressed on it, never waited for. */
  private boolean resubmittedOwnNudge(
      String screen, String session, String taskId, @Nullable String alias) {
    boolean own = hosting.runtime().draft(screen).filter(AgentWindows::isNudge).isPresent();
    if (!own || !hosting.host().submitTaskWindow(session, taskId)) {
      return false;
    }
    log.atInfo()
        .setMessage("nudge resubmitted")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", alias)
        .addKeyValue("cause", "its own nudge sat unsent in the agent's input")
        .log();
    return true;
  }

  public boolean cold(String taskId) {
    return stateService
        .task(stateService.canonicalTaskId(taskId))
        .filter(windows::cold)
        .isPresent();
  }

  /**
   * Whether the session's log was written after {@code millis}; false where it keeps none jagt can
   * find.
   */
  public boolean loggedSince(String taskId, long millis) {
    return stateService
        .task(stateService.canonicalTaskId(taskId))
        .map(TaskState::worktreePath)
        .flatMap(worktree -> hosting.runtime().sessionLogOf(Path.of(worktree)))
        .filter(log -> FileStamps.modified(log) > millis)
        .isPresent();
  }

  /**
   * A line typed over a draft is glued to it and sent as the human's, so the nudge waits for an
   * empty prompt.
   */
  private void nudgeOnceSent(String session, String taskId, @Nullable String alias) {
    if (!draftWaits.add(taskId)) {
      return;
    }
    log.atInfo()
        .setMessage("nudge waits")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", alias)
        .addKeyValue("cause", "the human is typing in the agent's window")
        .log();
    Duration draftPoll = windows.draftPoll();
    Thread.startVirtualThread(() -> {
      try {
        while (windows.running(session, taskId) && windows.composing(session, taskId)) {
          Thread.sleep(draftPoll);
        }
        stateService
            .task(taskId)
            .ifPresent(task -> {
              if (windows.cold(task)) {
                freshStarts.startWhenFree(taskId, task, IDLE_PAST_CACHE);
              } else if (windows.running(session, taskId)
                  && hosting.host().nudgeTaskWindow(session, taskId, AgentWindows.NUDGE)) {
                windows.undelivered(session, taskId, AgentWindows.NUDGE);
              }
            });
      } catch (InterruptedException stopped) {
        Thread.currentThread().interrupt();
      } finally {
        draftWaits.remove(taskId);
      }
    });
  }

  /**
   * Enter pressed on jagt's own nudge left in the input of an idle session; never on any other
   * text.
   */
  public boolean resubmitUnsentNudge(String taskId) {
    String id = stateService.canonicalTaskId(taskId);
    String session = windows.session(id);
    try {
      boolean unsent =
          hosting
              .host()
              .screenOf(session, id)
              .filter(shown -> !hosting.runtime().working(shown))
              .flatMap(hosting.runtime()::draft)
              .filter(AgentWindows::isNudge)
              .isPresent();
      if (unsent && cold(id)) {
        stateService
            .task(id)
            .ifPresent(task -> freshStarts.startWhenFree(id, task, IDLE_PAST_CACHE));
        return false;
      }
      return unsent && hosting.host().submitTaskWindow(session, id);
    } catch (RuntimeException unread) {
      log.atDebug()
          .setMessage("session screen unread")
          .addKeyValue("task", id)
          .addKeyValue("cause", unread.toString())
          .log();
      return false;
    }
  }

  /** One read of a standing fresh-start wait. */
  public void startWaitingFreshWhenFree(String taskId) {
    freshStarts.startWaitingWhenFree(taskId);
  }

  /**
   * The stop is lifted already and the task's tier raised: start the session again on the tier it
   * names, the work carried over.
   */
  public String restartOnTier(String taskId) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    ModelTier tier = task.work().tier();
    if (tier == null) {
      throw Refusal.byState("Task " + taskId + " has no model tier to start on");
    }
    freshStarts.startFresh(taskId, task, "escalated to a higher model tier", tier);
    return "session of "
        + taskId
        + " started again on "
        + (tier.current().isBlank() ? "the agent's default model" : tier.current());
  }

  /**
   * `plan` = the agent plans first and the plan is approved on the board; `auto` = straight to
   * work.
   */
  static LaunchMode launchMode(@Nullable String mode) {
    if (mode == null || mode.equalsIgnoreCase("auto")) {
      return LaunchMode.WORK;
    }
    if (mode.equalsIgnoreCase("plan")) {
      return LaunchMode.PLAN;
    }
    throw new IllegalArgumentException("Unknown mode '" + mode + "'. Allowed: auto, plan");
  }
}

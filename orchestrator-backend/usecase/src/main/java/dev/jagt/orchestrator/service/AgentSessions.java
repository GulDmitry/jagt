package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.session.SessionHosting;
import dev.jagt.orchestrator.service.session.SessionLines;
import dev.jagt.orchestrator.service.session.SessionWindow;
import dev.jagt.orchestrator.service.worktree.FileStamps;
import dev.jagt.orchestrator.service.worktree.WorktreeFiles;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.launch.TaskName;
import dev.jagt.orchestrator.task.plan.Artifact;
import dev.jagt.orchestrator.task.text.Ansi;
import dev.jagt.orchestrator.task.usage.ModelTier;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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

  private static final int RESUBMITS = 3;

  /** The one line jagt types into a session; whatever else arrives there is the human's. */
  public static final String NUDGE =
      "task_context.md changed — re-read it now and follow the new instructions.";

  private final ConfigService configService;
  private final StateService stateService;
  private final SessionHosting hosting;
  private final FlowRules rules;

  /**
   * Never pruned: a lock dropped while a relay holds it lets a second writer in, and an entry costs
   * bytes.
   */
  private final ConcurrentHashMap<String, Object> relayLocks = new ConcurrentHashMap<>();

  private final Set<String> draftWaits = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<String, FreshWait> freshWaits = new ConcurrentHashMap<>();

  public void startAgent(
      String taskId, @Nullable String alias, Path worktreePath, boolean planMode) {
    openTab(taskId, alias, worktreePath, configService.load(), planMode);
  }

  /**
   * What provisioning wrote outside the worktree is the runtime's to undo, and only it knows what
   * that was.
   */
  public void forgetWorktree(Path worktreePath) {
    hosting.runtime().retireWorktree(worktreePath);
  }

  public void killWindows(String taskId) {
    hosting.host().killTaskWindows(agentSession(configService.load(), taskId), taskId);
  }

  /** Reserving the viewer is the default: one placed by hand should survive task cycles. */
  public Optional<String> viewerToClose() {
    ConfigService.ConfigFile config = configService.load();
    if (!stateService.tasks().isEmpty() || config.viewer().keepViewerOrDefault()) {
      return Optional.empty();
    }
    return Optional.of(hosting.host().sessionName(config.viewer().session()));
  }

  /** A session that cannot list its windows throws: unknown is never missing. */
  public SessionHost.WindowState windowState(String taskId) {
    return hosting.host().taskWindowState(agentSession(configService.load(), taskId), taskId);
  }

  /** When the task's window last printed, epoch millis, 0 where it never did. */
  public long printedAt(String taskId) {
    return hosting
        .host()
        .lastWindowActivityMillis(agentSession(configService.load(), taskId), taskId);
  }

  private TaskState requireTask(String taskId) {
    return stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
  }

  /** The window a task's session lives in, whether or not it is open now. */
  public SessionWindow windowOf(String taskId) {
    String id = stateService.canonicalTaskId(taskId);
    requireTask(id);
    return new SessionWindow(agentSession(configService.load(), id), id);
  }

  public String openTaskTab(String taskId, @Nullable String mode) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    boolean plan = planMode(mode);
    if (plan && !rules.planMayComeFirst(task.standing().status())) {
      throw Refusal.byState(
          "Task "
              + taskId
              + " is "
              + task.standing().status()
              + ", so it cannot open in plan mode: a plan is what comes before the code, not after"
              + " it");
    }
    stateService.reduce(taskId, (reducer, was) -> reducer.planAsked(was, plan));
    String session =
        openTab(
            taskId,
            task.ticket().alias(),
            Path.of(task.worktreePath()),
            configService.load(),
            plan);
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
        + (plan ? "; it plans first, and the plan is approved on the board with approve-plan" : "");
  }

  /** The worktree and the state entry stay. */
  public String closeTaskTab(String taskId) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    int killed = hosting.host().killTaskWindows(agentSession(configService.load(), taskId), taskId);
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
        .screenOf(agentSession(configService.load(), id), id)
        .map(shown -> Ansi.strip(shown).stripTrailing());
  }

  /**
   * What the task's agent printed last before it exited; empty while it runs or once its window is
   * gone.
   */
  public Optional<String> exitTail(String taskId) {
    return hosting.host().exitTail(agentSession(configService.load(), taskId), taskId);
  }

  /**
   * Why the task's session waits at a prompt jagt never answers; empty where its window shows none.
   * Reads only.
   */
  public Optional<String> unansweredPrompt(String taskId) {
    return hosting
        .host()
        .screenOf(agentSession(configService.load(), taskId), taskId)
        .flatMap(hosting.runtime()::unansweredPrompt);
  }

  /** A session that is gone or dead is re-entered first: focus must always land on a live agent. */
  public Focused focusTask(String taskId) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    ConfigService.ConfigFile config = configService.load();
    String session = agentSession(config, taskId);
    String dedicatedTitle = hosting.host().sessionName(config.viewer().session());
    boolean revived;
    try {
      revived =
          hosting.host().taskWindowState(session, taskId) != SessionHost.WindowState.AGENT_RUNNING;
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
    ConfigService.ConfigFile config = configService.load();
    String session = agentSession(config, id);
    if (cold(task, config) && !promptOpen(session, id)) {
      return relayUnderCold(id, line);
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
    typed(agentSession(configService.load(), id), id, escape + line);
    log.atInfo()
        .setMessage("line run in agent")
        .addKeyValue("task", id)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("chars", line.length())
        .log();
    return "Run in the agent's window.";
  }

  private void typed(String session, String id, String text) {
    if (hosting.host().taskWindowState(session, id) != SessionHost.WindowState.AGENT_RUNNING
        || !hosting.host().nudgeTaskWindow(session, id, text)) {
      throw new IllegalStateException(
          "No "
              + hosting.runtime().displayName()
              + " session is running for "
              + id
              + " — restart the agent, then say it again.");
    }
    undelivered(session, id, text)
        .ifPresent(cause -> {
          throw new IllegalStateException(
              "The line was typed into " + id + "'s window, but " + cause + ".");
        });
  }

  public String press(String taskId, SessionHost.Key key) {
    String id = stateService.canonicalTaskId(taskId);
    requireTask(id);
    String session = agentSession(configService.load(), id);
    if (hosting.host().taskWindowState(session, id) != SessionHost.WindowState.AGENT_RUNNING
        || !hosting.host().pressTaskWindow(session, id, key)) {
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
    String session = agentSession(configService.load(), id);
    if (!running(session, id)
        || !shown(session, id).filter(hosting.runtime()::working).isPresent()) {
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
  public String relayUnder(String taskId, String addition, boolean fresh) {
    return under(taskId, addition, fresh ? ROUND_HANDED_BACK : null);
  }

  /** The same, always on a fresh session: its caller has already seen the conversation go cold. */
  public String relayUnderCold(String taskId, String addition) {
    return under(taskId, addition, "a prompt typed after the cache lifetime");
  }

  private String under(String taskId, String addition, @Nullable String freshBecause) {
    String id = stateService.canonicalTaskId(taskId);
    Path contextFile = Path.of(requireTask(id).worktreePath()).resolve(Artifact.CONTEXT.fileName());
    synchronized (relayLock(id)) {
      String standing =
          WorktreeFiles.read(contextFile)
              .map(CarriedBlock::without)
              .map(held -> held.contains(UNDER) ? held.substring(0, held.indexOf(UNDER)) : held)
              .orElse("");
      return relay(id, standing.isBlank() ? addition : standing + UNDER + addition, freshBecause);
    }
  }

  private static final String UNDER =
      "\n\n--- added by jagt; the instruction above still stands ---\n\n";

  private static final String ROUND_HANDED_BACK = "the round was already handed back";

  private String relay(String taskId, String instructions, @Nullable String freshBecause) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    Path contextFile = Path.of(task.worktreePath()).resolve(Artifact.CONTEXT.fileName());
    // One relay at a time per task: interleaving two writes to one file loses an instruction.
    synchronized (relayLock(taskId)) {
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
    // A file on disk doesn't wake a running agent session — nudge it directly.
    ConfigService.ConfigFile config = configService.load();
    String session = agentSession(config, taskId);
    if (freshBecause != null || cold(task, config)) {
      return relayFreshStart(taskId, task, config, freshBecause);
    }
    if (hosting.host().taskWindowState(session, taskId) == SessionHost.WindowState.AGENT_RUNNING) {
      Optional<String> screen = shown(session, taskId);
      if (screen.filter(hosting.runtime()::holdsDraft).isPresent()) {
        if (resubmittedOwnNudge(screen.get(), session, taskId, task.ticket().alias())) {
          return "Instructions written to task_context.md; the nudge already sat unsent in the"
              + " agent's window, so it was sent.";
        }
        nudgeOnceSent(session, taskId, task.ticket().alias());
        return "Instructions written to task_context.md; the human is typing in the agent's window,"
            + " so the nudge waits until they send it.";
      }
      if (hosting.host().nudgeTaskWindow(session, taskId, NUDGE)) {
        return undelivered(session, taskId, NUDGE)
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
            hosting.host().sessionName(config.viewer().session()),
            taskId,
            task.ticket().alias(),
            Path.of(task.worktreePath()));
    return "Instructions written to task_context.md; the agent session was down, so it was"
        + " re-entered to read and follow them.";
  }

  private String relayFreshStart(
      String taskId,
      TaskState task,
      ConfigService.ConfigFile config,
      @Nullable String freshBecause) {
    boolean started =
        startFreshWhenFree(
            taskId,
            task,
            config,
            freshBecause != null ? freshBecause : "conversation idle past its cache lifetime");
    return started
        ? "Instructions written to task_context.md; a fresh session was started to read them with"
            + " task_notes.md."
        : "Instructions written to task_context.md; the human is in the agent's window, so the"
            + " fresh session starts when they are done.";
  }

  private boolean running(String session, String taskId) {
    return hosting.host().taskWindowState(session, taskId) == SessionHost.WindowState.AGENT_RUNNING;
  }

  /**
   * A screen that cannot be read holds no draft we know of, so the nudge goes ahead as for an empty
   * prompt.
   */
  private boolean composing(String session, String taskId) {
    try {
      return hosting
          .host()
          .screenOf(session, taskId)
          .filter(hosting.runtime()::holdsDraft)
          .isPresent();
    } catch (RuntimeException unread) {
      log.atDebug()
          .setMessage("session screen unread")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", unread.toString())
          .log();
      return false;
    }
  }

  /** Whether the human has an unsent draft in the task's window. */
  public boolean composing(String taskId) {
    return composing(agentSession(configService.load(), taskId), taskId);
  }

  /** Our own nudge left unsent is no human's draft: Enter is pressed on it, never waited for. */
  private boolean resubmittedOwnNudge(
      String screen, String session, String taskId, @Nullable String alias) {
    boolean own = hosting.runtime().draft(screen).filter(AgentSessions::isNudge).isPresent();
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

  /** A screen that cannot be read is no screen. */
  private Optional<String> shown(String session, String taskId) {
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

  public boolean cold(String taskId) {
    return stateService
        .task(stateService.canonicalTaskId(taskId))
        .filter(task -> cold(task, configService.load()))
        .isPresent();
  }

  private boolean cold(TaskState task, ConfigService.ConfigFile config) {
    return SessionPhase.cold(
        task.work().session(), hosting.runtime(), config.agent().cacheLifetimeOrDefault());
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
    Duration draftPoll = configService.load().agent().draftPollOrDefault();
    Thread.startVirtualThread(() -> {
      try {
        while (running(session, taskId) && composing(session, taskId)) {
          Thread.sleep(draftPoll);
        }
        stateService
            .task(taskId)
            .ifPresent(task -> {
              ConfigService.ConfigFile config = configService.load();
              if (cold(task, config)) {
                startFreshWhenFree(
                    taskId, task, config, "conversation idle past its cache lifetime");
              } else if (running(session, taskId)
                  && hosting.host().nudgeTaskWindow(session, taskId, NUDGE)) {
                undelivered(session, taskId, NUDGE);
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
   * An Enter lost to a turn ending as it landed leaves the line in the input, so it is pressed
   * again.
   */
  private Optional<String> undelivered(String session, String taskId, String line) {
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
    try {
      return hosting
          .host()
          .screenOf(session, taskId)
          .flatMap(hosting.runtime()::draft)
          .filter(line.strip()::startsWith)
          .isPresent();
    } catch (RuntimeException unread) {
      log.atDebug()
          .setMessage("session screen unread")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", unread.toString())
          .log();
      return false;
    }
  }

  /**
   * Enter pressed on jagt's own nudge left in the input of an idle session; never on any other
   * text.
   */
  public boolean resubmitUnsentNudge(String taskId) {
    String id = stateService.canonicalTaskId(taskId);
    String session = agentSession(configService.load(), id);
    try {
      boolean unsent =
          hosting
              .host()
              .screenOf(session, id)
              .filter(shown -> !hosting.runtime().working(shown))
              .flatMap(hosting.runtime()::draft)
              .filter(AgentSessions::isNudge)
              .isPresent();
      if (unsent && cold(id)) {
        stateService
            .task(id)
            .ifPresent(task ->
                startFreshWhenFree(
                    id, task, configService.load(), "conversation idle past its cache lifetime"));
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

  /**
   * A wrapped input shows the nudge's first row alone, which must still be most of it to be ours.
   */
  private static boolean isNudge(String draft) {
    return NUDGE.startsWith(draft) && draft.length() >= NUDGE.length() / 2;
  }

  private boolean startFreshWhenFree(
      String taskId, TaskState task, ConfigService.ConfigFile config, String cause) {
    String session = agentSession(config, taskId);
    if (!humanHolds(session, taskId)) {
      startFresh(taskId, task, config, cause);
      return true;
    }
    freshStartWaits(taskId, task.ticket().alias(), cause);
    return false;
  }

  /** A window that is not running, or a task that is gone, holds nobody. */
  private boolean humanHolds(String session, String taskId) {
    return running(session, taskId)
        && stateService.task(taskId).isPresent()
        && (composing(session, taskId) || promptOpen(session, taskId) || humanTurnOpen(taskId));
  }

  private boolean promptOpen(String session, String taskId) {
    return shown(session, taskId).flatMap(hosting.runtime()::unansweredPrompt).isPresent();
  }

  private boolean humanTurnOpen(String taskId) {
    return stateService
        .task(taskId)
        .filter(task ->
            hosting.runtime().humanExchangeOpen(Path.of(task.worktreePath()), Set.of(NUDGE)))
        .isPresent();
  }

  /**
   * The instructions are on disk already; what ends the wait is the human leaving, the window dying
   * or the task going.
   */
  private void freshStartWaits(String taskId, @Nullable String alias, String cause) {
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
  public void startWaitingFreshWhenFree(String taskId) {
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
    ConfigService.ConfigFile config = configService.load();
    Optional<Boolean> holds = humanHoldsRead(agentSession(config, taskId), taskId);
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
      startFresh(taskId, task.get(), config, wait.cause());
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
    startFresh(taskId, task, configService.load(), "escalated to a higher model tier", tier);
    return "session of "
        + taskId
        + " started again on "
        + (tier.current().isBlank() ? "the agent's default model" : tier.current());
  }

  private void startFresh(
      String taskId, TaskState task, ConfigService.ConfigFile config, String cause) {
    startFresh(taskId, task, config, cause, null);
  }

  private void startFresh(
      String taskId,
      TaskState task,
      ConfigService.ConfigFile config,
      String cause,
      @Nullable ModelTier at) {
    log.atInfo()
        .setMessage("agent session started fresh")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("cause", cause)
        .log();
    Path contextFile = Path.of(task.worktreePath()).resolve(Artifact.CONTEXT.fileName());
    synchronized (relayLock(taskId)) {
      WorktreeFiles.read(contextFile)
          .ifPresent(held -> WorktreeFiles.write(contextFile, hosting.carried().after(held, task)));
    }
    openTab(taskId, task.ticket().alias(), Path.of(task.worktreePath()), config, false, at);
  }

  /**
   * One monitor per task id, so relays to one task serialise while different tasks never wait on
   * each other.
   */
  private Object relayLock(String taskId) {
    return relayLocks.computeIfAbsent(taskId, id -> new Object());
  }

  private String openTab(
      String taskId,
      @Nullable String alias,
      Path worktreePath,
      ConfigService.ConfigFile config,
      boolean planMode) {
    return openTab(taskId, alias, worktreePath, config, planMode, null);
  }

  /** {@code at} is the tier a stop moved the session to; without one it starts on its own model. */
  private String openTab(
      String taskId,
      @Nullable String alias,
      Path worktreePath,
      ConfigService.ConfigFile config,
      boolean planMode,
      @Nullable ModelTier at) {
    String own = modelFor(worktreePath, config);
    ModelTier tier =
        at == null ? ModelTier.above(own, config.agent().tiersFor(hosting.runtime())) : at;
    stateService.reduce(taskId, (reducer, task) -> reducer.sessionStartedOver(task).withTier(tier));
    String session = agentSession(config, taskId);
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
  private String modelFor(Path worktree, ConfigService.ConfigFile config) {
    List<String> models = config.agent().modelsFor(hosting.runtime());
    return WorktreeFiles.read(worktree.resolve(Artifact.NOTES.fileName()))
        .flatMap(TaskNotes::nextModel)
        .or(() -> Optional.of(config.agent().modelOrInherited()))
        .filter(models::contains)
        .orElse("");
  }

  private String agentSession(ConfigService.ConfigFile config, String taskId) {
    String base = hosting.host().sessionName(config.viewer().session());
    return config.viewer().sharedView() ? base : base + "-" + TaskName.slug(taskId);
  }

  /**
   * `plan` = the agent plans first and the plan is approved on the board; `auto` = straight to
   * work.
   */
  static boolean planMode(@Nullable String mode) {
    if (mode == null || mode.equalsIgnoreCase("auto")) {
      return false;
    }
    if (mode.equalsIgnoreCase("plan")) {
      return true;
    }
    throw new IllegalArgumentException("Unknown mode '" + mode + "'. Allowed: auto, plan");
  }
}

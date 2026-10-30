package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.Ansi;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.SessionPhase;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.port.SessionHost;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.task.Artifact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which agent runs here is never assumed: window titles, liveness and the words in these messages all come from
 * the runtime.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSessions {

    private static final Duration DRAFT_POLL = Duration.ofSeconds(1);
    private static final Duration SUBMIT_WAIT = Duration.ofMillis(700);
    private static final int RESUBMITS = 3;

    /** The one line jagt types into a session; whatever else arrives there is the human's. */
    public static final String NUDGE = "task_context.md changed — re-read it now and follow the new instructions.";

    private final ConfigService configService;
    private final StateService stateService;
    private final SessionHost sessions;
    private final TerminalDriver terminalDriver;
    private final AgentRuntime agentRuntime;
    /** Never pruned: a lock dropped while a relay holds it lets a second writer in, and an entry costs bytes. */
    private final ConcurrentHashMap<String, Object> relayLocks = new ConcurrentHashMap<>();
    private final Set<String> draftWaits = ConcurrentHashMap.newKeySet();

    public void startAgent(String taskId, @Nullable String alias, Path worktreePath, boolean planMode) {
        openTab(taskId, alias, worktreePath, configService.load(), planMode);
    }

    /** What provisioning wrote outside the worktree is the runtime's to undo, and only it knows what that was. */
    public void forgetWorktree(Path worktreePath) {
        agentRuntime.retireWorktree(worktreePath);
    }

    public void killWindows(String taskId) {
        sessions.killTaskWindows(agentSession(configService.load(), taskId), taskId);
    }

    /** Reserving the viewer is the default: one placed by hand should survive task cycles. */
    public boolean closeViewerIfNoTasksLeft() {
        ConfigService.ConfigFile config = configService.load();
        if (!stateService.tasks().isEmpty() || config.viewer().keepViewerOrDefault()) {
            return false;
        }
        terminalDriver.closeViewerWindow(sessions.sessionName(config.viewer().session()));
        return true;
    }

    /** A session that cannot list its windows throws: unknown is never missing. */
    public SessionHost.WindowState windowState(String taskId) {
        return sessions.taskWindowState(agentSession(configService.load(), taskId), taskId);
    }

    /** When the task's window last printed, epoch millis, 0 where it never did. */
    public long printedAt(String taskId) {
        return sessions.lastWindowActivityMillis(agentSession(configService.load(), taskId), taskId);
    }

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> Refusal.noSuchTask(taskId));
    }

    public String openTaskTab(String taskId, @Nullable String mode) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        boolean plan = planMode(mode);
        if (plan && !FlowRules.planMayComeFirst(task.status())) {
            throw Refusal.byState("Task " + taskId + " is " + task.status()
                    + ", so it cannot open in plan mode: a plan is what comes before the code, not after it");
        }
        stateService.updateTask(taskId, was -> TaskReducer.planAsked(was, plan));
        String session = openTab(taskId, task.alias(), Path.of(task.worktreePath()), configService.load(), plan);
        return "New " + agentRuntime.displayName() + " session started for " + taskId + " in tab '"
                + taskId + "' of session '" + session + "' (worktree " + task.worktreePath() + ")"
                + (plan ? "; it plans first, and the plan is approved on the board with approve-plan" : "");
    }

    /** The worktree and the state entry stay. */
    public String closeTaskTab(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        int killed = sessions.killTaskWindows(
                agentSession(configService.load(), taskId), taskId);
        return killed == 0
                ? "No tab named '" + taskId + "' found — the session was already closed."
                : "Closed " + killed + " tab(s) for " + taskId + "; the "
                        + agentRuntime.displayName() + " session is terminated. Worktree kept: "
                        + task.worktreePath();
    }

    /** What the task's session shows now, colour codes removed; empty when no window holds it. Changes nothing. */
    public Optional<String> screen(String taskId) {
        String id = stateService.canonicalTaskId(taskId);
        requireTask(id);
        return sessions.screenOf(agentSession(configService.load(), id), id)
                .map(shown -> Ansi.strip(shown).stripTrailing());
    }

    /** What the task's agent printed last before it exited; empty while it runs or once its window is gone. */
    public Optional<String> exitTail(String taskId) {
        return sessions.exitTail(agentSession(configService.load(), taskId), taskId);
    }

    /** Why the task's session waits at a prompt jagt never answers; empty where its window shows none. Reads only. */
    public Optional<String> unansweredPrompt(String taskId) {
        return sessions.screenOf(agentSession(configService.load(), taskId), taskId)
                .flatMap(agentRuntime::unansweredPrompt);
    }

    /** A session that is gone or dead is re-entered first: focus must always land on a live agent. */
    public String focusTask(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        ConfigService.ConfigFile config = configService.load();
        String session = agentSession(config, taskId);
        String dedicatedTitle = sessions.sessionName(config.viewer().session());
        boolean revived;
        try {
            revived = sessions.taskWindowState(session, taskId) != SessionHost.WindowState.AGENT_RUNNING;
            if (revived) {
                sessions.reviveTaskWindow(session, dedicatedTitle, taskId, task.alias(),
                        Path.of(task.worktreePath()));
            }
            sessions.focusTaskWindow(session, dedicatedTitle, taskId);
        } catch (SessionHost.WindowsUnreadable unread) {
            throw Refusal.byState("Nothing was focused: " + unread.getMessage());
        }
        return "Focused tab '" + taskId + "'" + viewer(terminalDriver.reveal(dedicatedTitle))
                + (revived ? "; the session was down, re-entered its " + agentRuntime.displayName()
                        + " session" : "");
    }

    /** What is left for the human to do about the viewer, which only the terminal can say. */
    private static String viewer(TerminalDriver.Revealed revealed) {
        return switch (revealed) {
            case WINDOW -> " and raised the agents window";
            case UNREACHABLE_TAB -> " — the agents viewer is a tab this terminal cannot select; click it"
                    + " yourself";
            case NOT_RUNNING -> " — no agents viewer is open; the session is there, nothing is showing it";
        };
    }

    public AgentRuntime.Admission admit(String taskId, Path directory) {
        return agentRuntime.admit(Path.of(requireTask(stateService.canonicalTaskId(taskId)).worktreePath()), directory);
    }

    public String writeTaskContext(String taskId, String instructions) {
        return relay(taskId, instructions, null);
    }

    /**
     * A line the human types, typed into the running session as they would have typed it in its window. Never a
     * relay: that OVERWRITES task_context.md, and the round's brief is still what the agent is working from.
     */
    public String say(String taskId, String line) {
        String id = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(id);
        String session = agentSession(configService.load(), id);
        if (sessions.taskWindowState(session, id) != SessionHost.WindowState.AGENT_RUNNING
                || !sessions.nudgeTaskWindow(session, id, line)) {
            throw new IllegalStateException("No " + agentRuntime.displayName() + " session is running for "
                    + id + " — restart the agent, then say it again.");
        }
        undelivered(session, id, line).ifPresent(cause -> {
            throw new IllegalStateException("The line was typed into " + id + "'s window, but " + cause + ".");
        });
        log.atInfo().setMessage("line said to agent").addKeyValue("task", id)
                .addKeyValue("alias", task.alias()).addKeyValue("said", OneLine.clipped(line)).log();
        return "Said to the agent.";
    }

    /** Work on a round already handed back needs the notes and the brief, not the conversation that wrote them. */
    public String relayFresh(String taskId, String instructions) {
        return relay(taskId, instructions, ROUND_HANDED_BACK);
    }

    /** Keeps what the context file holds above its last addition, so an instruction still pending is not lost. */
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
            String standing = WorktreeFiles.read(contextFile).map(held -> held.contains(UNDER)
                    ? held.substring(0, held.indexOf(UNDER)) : held).orElse("");
            return relay(id, standing.isBlank() ? addition : standing + UNDER + addition, freshBecause);
        }
    }

    private static final String UNDER = "\n\n--- added by jagt; the instruction above still stands ---\n\n";

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
        log.atInfo().setMessage("instructions relayed").addKeyValue("task", taskId)
                .addKeyValue("alias", task.alias())
                .addKeyValue("chars", instructions.length())
                .addKeyValue("said", OneLine.clipped(instructions.lines().findFirst().orElse("(empty)")))
                .log();
        // A file on disk doesn't wake a running agent session — nudge it directly.
        ConfigService.ConfigFile config = configService.load();
        String session = agentSession(config, taskId);
        if (freshBecause != null || cold(task, config)) {
            startFresh(taskId, task, config, freshBecause != null ? freshBecause
                    : "conversation idle past its cache lifetime");
            return "Instructions written to task_context.md; a fresh session was started to read them with"
                    + " task_notes.md.";
        }
        if (sessions.taskWindowState(session, taskId) == SessionHost.WindowState.AGENT_RUNNING) {
            Optional<String> screen = shown(session, taskId);
            if (screen.filter(agentRuntime::holdsDraft).isPresent()) {
                if (resubmittedOwnNudge(screen.get(), session, taskId, task.alias())) {
                    return "Instructions written to task_context.md; the nudge already sat unsent in the agent's"
                            + " window, so it was sent.";
                }
                nudgeOnceSent(session, taskId, task.alias());
                return "Instructions written to task_context.md; the human is typing in the agent's window, so the"
                        + " nudge waits until they send it.";
            }
            if (sessions.nudgeTaskWindow(session, taskId, NUDGE)) {
                return undelivered(session, taskId, NUDGE)
                        .map(cause -> "Instructions written to task_context.md, but the nudge failed: " + cause + ".")
                        .orElse("Instructions written to task_context.md and the agent was nudged to re-read them.");
            }
        }
        log.atInfo().setMessage("agent session re-entered").addKeyValue("task", taskId)
                .addKeyValue("alias", task.alias())
                .addKeyValue("cause", "session was down when instructions were relayed")
                .log();
        // A re-entered session reads task_context.md again, so a relay cannot dead-end against a dead agent.
        sessions.reviveTaskWindow(session, sessions.sessionName(config.viewer().session()), taskId,
                task.alias(), Path.of(task.worktreePath()));
        return "Instructions written to task_context.md; the agent session was down, so it was re-entered"
                + " to read and follow them.";
    }

    private boolean running(String session, String taskId) {
        return sessions.taskWindowState(session, taskId) == SessionHost.WindowState.AGENT_RUNNING;
    }

    /** A screen that cannot be read holds no draft we know of, so the nudge goes ahead as for an empty prompt. */
    private boolean composing(String session, String taskId) {
        try {
            return sessions.screenOf(session, taskId).filter(agentRuntime::holdsDraft).isPresent();
        } catch (RuntimeException unread) {
            log.atDebug().setMessage("session screen unread").addKeyValue("task", taskId)
                    .addKeyValue("cause", unread.toString()).log();
            return false;
        }
    }

    /** Our own nudge left unsent is no human's draft: Enter is pressed on it, never waited for. */
    private boolean resubmittedOwnNudge(String screen, String session, String taskId, @Nullable String alias) {
        boolean own = agentRuntime.draft(screen).filter(AgentSessions::isNudge).isPresent();
        if (!own || !sessions.submitTaskWindow(session, taskId)) {
            return false;
        }
        log.atInfo().setMessage("nudge resubmitted").addKeyValue("task", taskId).addKeyValue("alias", alias)
                .addKeyValue("cause", "its own nudge sat unsent in the agent's input").log();
        return true;
    }

    /** A screen that cannot be read is no screen. */
    private Optional<String> shown(String session, String taskId) {
        try {
            return sessions.screenOf(session, taskId);
        } catch (RuntimeException unread) {
            log.atDebug().setMessage("session screen unread").addKeyValue("task", taskId)
                    .addKeyValue("cause", unread.toString()).log();
            return Optional.empty();
        }
    }

    private boolean cold(TaskState task, ConfigService.ConfigFile config) {
        long idleSince = SessionPhase.idleSince(task.session());
        return idleSince > 0
                && agentRuntime.pastCacheLifetime(idleSince, config.agent().cacheLifetimeOrDefault());
    }

    /** A line typed over a draft is glued to it and sent as the human's, so the nudge waits for an empty prompt. */
    private void nudgeOnceSent(String session, String taskId, @Nullable String alias) {
        if (!draftWaits.add(taskId)) {
            return;
        }
        log.atInfo().setMessage("nudge waits").addKeyValue("task", taskId).addKeyValue("alias", alias)
                .addKeyValue("cause", "the human is typing in the agent's window").log();
        Thread.startVirtualThread(() -> {
            try {
                while (running(session, taskId) && composing(session, taskId)) {
                    Thread.sleep(DRAFT_POLL);
                }
                stateService.task(taskId).ifPresent(task -> {
                    ConfigService.ConfigFile config = configService.load();
                    if (cold(task, config)) {
                        startFresh(taskId, task, config, "conversation idle past its cache lifetime");
                    } else if (running(session, taskId) && sessions.nudgeTaskWindow(session, taskId, NUDGE)) {
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

    /** An Enter lost to a turn ending as it landed leaves the line in the input, so it is pressed again. */
    private Optional<String> undelivered(String session, String taskId, String line) {
        try {
            for (int pressed = 0; pressed <= RESUBMITS; pressed++) {
                Thread.sleep(SUBMIT_WAIT);
                if (!stillTyped(session, taskId, line)) {
                    return Optional.empty();
                }
                if (pressed < RESUBMITS && !sessions.submitTaskWindow(session, taskId)) {
                    return Optional.of("its window closed before the line left its input");
                }
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return Optional.of("jagt stopped before the line was seen to leave the input");
        }
        String cause = "the line still sat unsent in the agent's input after Enter was pressed " + (RESUBMITS + 1)
                + " times";
        log.atWarn().setMessage("nudge unsent").addKeyValue("task", taskId).addKeyValue("cause", cause).log();
        return Optional.of(cause);
    }

    /** A screen that cannot be read shows no line of ours, so nothing is pressed blind. */
    private boolean stillTyped(String session, String taskId, String line) {
        try {
            return sessions.screenOf(session, taskId).flatMap(agentRuntime::draft)
                    .filter(line.strip()::startsWith).isPresent();
        } catch (RuntimeException unread) {
            log.atDebug().setMessage("session screen unread").addKeyValue("task", taskId)
                    .addKeyValue("cause", unread.toString()).log();
            return false;
        }
    }

    /** Enter pressed on jagt's own nudge left in the input of an idle session; never on any other text. */
    public boolean resubmitUnsentNudge(String taskId) {
        String id = stateService.canonicalTaskId(taskId);
        String session = agentSession(configService.load(), id);
        try {
            boolean unsent = sessions.screenOf(session, id).filter(shown -> !agentRuntime.working(shown))
                    .flatMap(agentRuntime::draft).filter(AgentSessions::isNudge).isPresent();
            return unsent && sessions.submitTaskWindow(session, id);
        } catch (RuntimeException unread) {
            log.atDebug().setMessage("session screen unread").addKeyValue("task", id)
                    .addKeyValue("cause", unread.toString()).log();
            return false;
        }
    }

    /** A wrapped input shows the nudge's first row alone, which must still be most of it to be ours. */
    private static boolean isNudge(String draft) {
        return NUDGE.startsWith(draft) && draft.length() >= NUDGE.length() / 2;
    }

    private void startFresh(String taskId, TaskState task, ConfigService.ConfigFile config, String cause) {
        log.atInfo().setMessage("agent session started fresh").addKeyValue("task", taskId)
                .addKeyValue("alias", task.alias()).addKeyValue("cause", cause).log();
        stateService.updateTask(taskId, TaskReducer::sessionStartedOver);
        openTab(taskId, task.alias(), Path.of(task.worktreePath()), config, false);
    }

    /** One monitor per task id, so relays to one task serialise while different tasks never wait on each other. */
    private Object relayLock(String taskId) {
        return relayLocks.computeIfAbsent(taskId, id -> new Object());
    }

    private String openTab(String taskId, @Nullable String alias, Path worktreePath, ConfigService.ConfigFile config,
                           boolean planMode) {
        String session = agentSession(config, taskId);
        sessions.openTaskWindow(session, sessions.sessionName(config.viewer().session()), taskId,
                alias, worktreePath, planMode, modelFor(worktreePath, config));
        return session;
    }

    /** The notes' `next-model:` wins over `agent.model`; blank is the CLI's own default. */
    private String modelFor(Path worktree, ConfigService.ConfigFile config) {
        return agentRuntime.choosesModel()
                ? WorktreeFiles.read(worktree.resolve(Artifact.NOTES.fileName())).flatMap(TaskNotes::nextModel)
                        .or(() -> Optional.of(config.agent().modelOrInherited()))
                        .filter(agentRuntime.models()::contains).orElse("")
                : "";
    }

    /** Whether the human has an unsent draft in the task's window. */
    public boolean composing(String taskId) {
        return composing(agentSession(configService.load(), taskId), taskId);
    }

    private String agentSession(ConfigService.ConfigFile config, String taskId) {
        String base = sessions.sessionName(config.viewer().session());
        return config.viewer().sharedView()
                ? base
                : base + "-" + TaskName.slug(taskId);
    }

    /** `plan` = the agent plans first and the plan is approved on the board; `auto` = straight to work. */
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

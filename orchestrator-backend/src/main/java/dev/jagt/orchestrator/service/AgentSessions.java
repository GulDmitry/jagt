package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskLabel;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.port.TerminalDriver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything about an agent's SESSION: the tmux window it lives in, focusing it for the human, killing it, and
 * relaying an instruction into its worktree.
 *
 * <p>Which agent runs here is never assumed: window titles, liveness and the words in these messages come from
 * {@code TmuxService} and {@link AgentRuntime#displayName()}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSessions implements dev.jagt.orchestrator.port.AgentPresence {

    private final ConfigService configService;
    private final StateService stateService;
    private final TmuxService tmuxService;
    private final TerminalDriver terminalDriver;
    private final AgentRuntime agentRuntime;
    /** Per-task relay monitors; a handful of entries, one per task ever relayed to in this session. */
    private final ConcurrentHashMap<String, Object> relayLocks = new ConcurrentHashMap<>();

    /** Starts the agent in its tmux window and returns the session name. */
    public String startAgent(String taskId, String alias, Path worktreePath, boolean planMode) {
        return openTab(taskId, alias, worktreePath, configService.load(), planMode);
    }

    /** Kills a task's windows outright — used by `done`, which then deletes the worktree under them. */
    public int killWindows(String taskId) {
        return tmuxService.killTaskWindows(agentSession(configService.load(), taskId), taskId);
    }

    /**
     * Closes the agents viewer when the LAST task is gone and the human has not asked to keep it. Reserving it
     * is the default: a viewer placed by hand (dragged into a window or a group) should survive task cycles.
     */
    public boolean closeViewerIfNoTasksLeft() {
        ConfigService.ConfigFile config = configService.load();
        if (!stateService.tasks().isEmpty() || config.viewer().keepViewerOrDefault()) {
            return false;
        }
        terminalDriver.closeViewerWindow(tmuxService.sessionName(config.viewer().tmuxSession()));
        return true;
    }

    /** The tmux session holding the task's window: the shared one, or its own in viewMode tab-per-task. */
    public String sessionOf(String taskId) {
        String canonical = stateService.canonicalTaskId(taskId);
        requireTask(canonical);
        return agentSession(configService.load(), canonical);
    }

    /** Whether the task's agent is alive right now — the one question a projection deliberately does not ask. */
    @Override
    public boolean agentLive(String taskId) {
        return tmuxService.taskWindowState(agentSession(configService.load(), taskId), taskId)
                == TmuxService.WindowState.AGENT_RUNNING;
    }

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " not found in state.json"));
    }

    /**
     * Recovery path: the worktree and state entry exist, but the agent's tmux
     * window is gone (closed, crashed, or the agent died). Spawns a fresh one.
     */
    public String openTaskTab(String taskId, String mode) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        String session = openTab(taskId, task.alias(), Path.of(task.worktreePath()), configService.load(),
                planMode(mode));
        return "New " + agentRuntime.displayName() + " session started for " + taskId + " in tmux window '"
                + taskId + "' of session '" + session + "' (worktree " + task.worktreePath() + ")"
                + (planMode(mode) ? " in PLAN MODE" : "");
    }

    /** Closes the task's tmux window(s), killing the agent session; worktree and state stay. */
    public String closeTaskTab(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        int killed = tmuxService.killTaskWindows(
                agentSession(configService.load(), taskId), taskId);
        return killed == 0
                ? "No tmux window named '" + taskId + "' found — the session was already closed."
                : "Closed " + killed + " tmux window(s) for " + taskId + "; the "
                        + agentRuntime.displayName() + " session is terminated. Worktree kept: "
                        + task.worktreePath();
    }

    /**
     * Brings the task's agent window to the user's screen. If the session was
     * closed (window gone), a fresh agent session is started first — focus
     * must always land somewhere.
     */
    public String focusTask(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        ConfigService.ConfigFile config = configService.load();
        String session = agentSession(config, taskId);
        String dedicatedTitle = tmuxService.sessionName(config.viewer().tmuxSession());
        boolean respawned = false;
        Path worktreePath = Path.of(task.worktreePath());
        switch (tmuxService.taskWindowState(session, taskId)) {
            case MISSING -> {
                tmuxService.openTaskWindow(session, dedicatedTitle, taskId, task.alias(), worktreePath, false);
                respawned = true;
            }
            case DEAD_SHELL -> {
                // The window survived only for post-mortem inspection; focusing it
                // must hand the user a live agent, not a dead prompt.
                tmuxService.killTaskWindows(session, taskId);
                tmuxService.openTaskWindow(session, dedicatedTitle, taskId, task.alias(), worktreePath, false);
                respawned = true;
            }
            case AGENT_RUNNING -> {
            }
        }
        tmuxService.focusTaskWindow(session, dedicatedTitle, taskId);
        boolean raised = terminalDriver.reveal(dedicatedTitle);
        return "Focused tmux window '" + taskId + "'"
                + (raised
                        ? " and raised the agents window"
                        : " — but the agents viewer is a TAB, not a window: the terminal has no API to"
                                + " switch tabs, so click the agents tab yourself (or keep it as its own window)")
                + (respawned ? "; the session was dead, started a fresh " + agentRuntime.displayName() + " session" : "");
    }

    public String writeTaskContext(String taskId, String instructions) {
        return relay(taskId, instructions, false);
    }

    /**
     * Adds to whatever the agent has not read yet instead of replacing it. Two independent flows relay to the
     * same file — a review sweep's brief and ship's "post your drafted replies" — and truncating lost one of
     * them outright: a sweep that had just handed over four unresolved comments, overwritten a second later by
     * a ship, left the agent with no idea the comments existed and the task sitting at CI_POLLING as if the
     * review were clean. Supplementary instructions append; a NEW round of work still replaces (see the sweep).
     */
    public String appendTaskContext(String taskId, String instructions) {
        return relay(taskId, instructions, true);
    }

    private String relay(String taskId, String instructions, boolean append) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        Path contextFile = Path.of(task.worktreePath()).resolve("task_context.md");
        // One relay at a time per task: the sweep runs unattended every 60s while a human can ship at any
        // moment, and interleaving two writes to one file is how an instruction disappears.
        synchronized (relayLock(taskId)) {
            WorktreeFiles.write(contextFile, append
                    ? WorktreeFiles.read(contextFile).map(existing -> existing + "\n\n" + instructions)
                            .orElse(instructions)
                    : instructions);
        }
        // The opening line only: a brief runs to hundreds of lines.
        log.atInfo().addKeyValue("task", taskId).addKeyValue("alias", task.alias())
                .addKeyValue("chars", instructions.length()).addKeyValue("appended", append)
                .log("-> agent {}: {}", TaskLabel.of(taskId, task.alias()),
                        instructions.lines().findFirst().orElse("(empty)"));
        // A file on disk doesn't wake a running agent session — nudge it directly.
        String session = agentSession(configService.load(), taskId);
        if (tmuxService.taskWindowState(session, taskId) == TmuxService.WindowState.AGENT_RUNNING
                && tmuxService.nudgeTaskWindow(session, taskId,
                        "The Master updated task_context.md — re-read it now and follow the new instructions.")) {
            return "Instructions written to task_context.md and the agent was nudged to re-read them.";
        }
        log.atInfo().addKeyValue("task", taskId).addKeyValue("alias", task.alias())
                .log("-> agent {}: its session was down, respawning it to read the instructions",
                        TaskLabel.of(taskId, task.alias()));
        // Session not running (killed / crashed / never started): respawn it — a fresh agent session
        // reads task_context.md on start and acts on the relayed instruction, so ship/review can't
        // dead-end against a dead agent.
        openTab(taskId, task.alias(), Path.of(task.worktreePath()), configService.load(), false);
        return "Instructions written to task_context.md; the agent session was down, so it was respawned"
                + " to read and follow them.";
    }

    /** One monitor per task id, so relays to one task serialise while different tasks never wait on each other. */
    private Object relayLock(String taskId) {
        return relayLocks.computeIfAbsent(taskId, id -> new Object());
    }

    /** Starts the agent in a tmux window and returns its session name. */
    private String openTab(String taskId, String alias, Path worktreePath, ConfigService.ConfigFile config,
                           boolean planMode) {
        String session = agentSession(config, taskId);
        tmuxService.openTaskWindow(session, tmuxService.sessionName(config.viewer().tmuxSession()), taskId,
                alias, worktreePath, planMode);
        return session;
    }

    /**
     * viewMode "shared" (default): every task is a tmux window in ONE session,
     * one terminal tab total. viewMode "tab-per-task": each task gets its own
     * session, shown as its own Warp tab in the current window.
     */
    private String agentSession(ConfigService.ConfigFile config, String taskId) {
        String base = tmuxService.sessionName(config.viewer().tmuxSession());
        return config.viewer().sharedView()
                ? base
                : base + "-" + taskId;
    }

    /** `plan` = the agent plans first and the human approves the plan in its window; `auto` = straight to work. */
    static boolean planMode(String mode) {
        if (mode == null || mode.equalsIgnoreCase("auto")) {
            return false;
        }
        if (mode.equalsIgnoreCase("plan")) {
            return true;
        }
        throw new IllegalArgumentException("Unknown mode '" + mode + "'. Allowed: auto, plan");
    }
}

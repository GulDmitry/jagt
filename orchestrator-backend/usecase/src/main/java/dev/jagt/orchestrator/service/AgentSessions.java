package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.SessionHost;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.port.TerminalDriver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which agent runs here is never assumed: window titles, liveness and the words in these messages all come from
 * the runtime.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSessions implements dev.jagt.orchestrator.port.AgentPresence {

    private final ConfigService configService;
    private final StateService stateService;
    private final SessionHost sessions;
    private final TerminalDriver terminalDriver;
    private final AgentRuntime agentRuntime;
    private final ConcurrentHashMap<String, Object> relayLocks = new ConcurrentHashMap<>();

    public String startAgent(String taskId, String alias, Path worktreePath, boolean planMode) {
        return openTab(taskId, alias, worktreePath, configService.load(), planMode);
    }

    public int killWindows(String taskId) {
        return sessions.killTaskWindows(agentSession(configService.load(), taskId), taskId);
    }

    /**
     * Reserving the viewer is the default: one placed by hand — dragged into a window or a group — should survive
     * task cycles.
     */
    public boolean closeViewerIfNoTasksLeft() {
        ConfigService.ConfigFile config = configService.load();
        if (!stateService.tasks().isEmpty() || config.viewer().keepViewerOrDefault()) {
            return false;
        }
        terminalDriver.closeViewerWindow(sessions.sessionName(config.viewer().tmuxSession()));
        return true;
    }

    public String sessionOf(String taskId) {
        String canonical = stateService.canonicalTaskId(taskId);
        requireTask(canonical);
        return agentSession(configService.load(), canonical);
    }

    @Override
    public boolean agentLive(String taskId) {
        return sessions.taskWindowState(agentSession(configService.load(), taskId), taskId)
                == SessionHost.WindowState.AGENT_RUNNING;
    }

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " not found in state.json"));
    }

    public String openTaskTab(String taskId, String mode) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        String session = openTab(taskId, task.alias(), Path.of(task.worktreePath()), configService.load(),
                planMode(mode));
        return "New " + agentRuntime.displayName() + " session started for " + taskId + " in tmux window '"
                + taskId + "' of session '" + session + "' (worktree " + task.worktreePath() + ")"
                + (planMode(mode) ? " in PLAN MODE" : "");
    }

    /** The worktree and the state entry stay. */
    public String closeTaskTab(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        int killed = sessions.killTaskWindows(
                agentSession(configService.load(), taskId), taskId);
        return killed == 0
                ? "No tmux window named '" + taskId + "' found — the session was already closed."
                : "Closed " + killed + " tmux window(s) for " + taskId + "; the "
                        + agentRuntime.displayName() + " session is terminated. Worktree kept: "
                        + task.worktreePath();
    }

    /** A session that is gone or dead is started fresh first: focus must always land somewhere. */
    public String focusTask(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        ConfigService.ConfigFile config = configService.load();
        String session = agentSession(config, taskId);
        String dedicatedTitle = sessions.sessionName(config.viewer().tmuxSession());
        boolean respawned = false;
        Path worktreePath = Path.of(task.worktreePath());
        switch (sessions.taskWindowState(session, taskId)) {
            case MISSING -> {
                sessions.openTaskWindow(session, dedicatedTitle, taskId, task.alias(), worktreePath, false);
                respawned = true;
            }
            case DEAD_SHELL -> {
                // The window survived only for post-mortem inspection; focusing it
                // must hand the user a live agent, not a dead prompt.
                sessions.killTaskWindows(session, taskId);
                sessions.openTaskWindow(session, dedicatedTitle, taskId, task.alias(), worktreePath, false);
                respawned = true;
            }
            case AGENT_RUNNING -> {
            }
        }
        sessions.focusTaskWindow(session, dedicatedTitle, taskId);
        return "Focused tmux window '" + taskId + "'" + viewer(terminalDriver.reveal(dedicatedTitle))
                + (respawned ? "; the session was dead, started a fresh " + agentRuntime.displayName() + " session" : "");
    }

    /** What is left for the human to do about the viewer, which only the terminal can say. */
    private static String viewer(TerminalDriver.Revealed revealed) {
        return switch (revealed) {
            case WINDOW -> " and raised the agents window";
            case UNREACHABLE_TAB -> " — the agents viewer is a TAB and this terminal has no API to select one,"
                    + " so click it yourself (or keep it as its own window)";
            case NOT_RUNNING -> " — no agents viewer is open; the session is there, nothing is showing it";
        };
    }

    public String writeTaskContext(String taskId, String instructions) {
        return relay(taskId, instructions, false);
    }

    /**
     * Adds to whatever the agent has not read yet instead of replacing it: independent flows relay to the same
     * file, and truncating loses one of them outright. A NEW round of work still replaces.
     */
    public String appendTaskContext(String taskId, String instructions) {
        return relay(taskId, instructions, true);
    }

    /**
     * Relays only what the agent has not already been handed. The poller reads the same round every interval
     * while the request stands still, and a relay does not merely write a file — it NUDGES the session, so an
     * unchanged brief would interrupt an agent every interval to re-decide comments it has already answered.
     *
     * @return false when the file already holds exactly this brief, so nothing was written and nobody nudged
     */
    public boolean relayIfChanged(String taskId, String instructions) {
        String id = stateService.canonicalTaskId(taskId);
        Path contextFile = Path.of(requireTask(id).worktreePath()).resolve("task_context.md");
        synchronized (relayLock(id)) {
            if (WorktreeFiles.read(contextFile).filter(instructions::equals).isPresent()) {
                return false;
            }
            writeTaskContext(id, instructions);
            return true;
        }
    }

    private String relay(String taskId, String instructions, boolean append) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        Path contextFile = Path.of(task.worktreePath()).resolve("task_context.md");
        // One relay at a time per task: a sweep runs unattended while a human can ship at any moment, and
        // interleaving two writes to one file is how an instruction disappears.
        synchronized (relayLock(taskId)) {
            WorktreeFiles.write(contextFile, append
                    ? WorktreeFiles.read(contextFile).map(existing -> existing + "\n\n" + instructions)
                            .orElse(instructions)
                    : instructions);
        }
        // The opening line only: a brief runs to hundreds of lines.
        log.atInfo().setMessage("instructions relayed").addKeyValue("task", taskId)
                .addKeyValue("alias", task.alias())
                .addKeyValue("chars", instructions.length())
                .addKeyValue("appended", append)
                .addKeyValue("said", instructions.lines().findFirst().orElse("(empty)"))
                .log();
        // A file on disk doesn't wake a running agent session — nudge it directly.
        String session = agentSession(configService.load(), taskId);
        if (sessions.taskWindowState(session, taskId) == SessionHost.WindowState.AGENT_RUNNING
                && sessions.nudgeTaskWindow(session, taskId,
                        "The Master updated task_context.md — re-read it now and follow the new instructions.")) {
            return "Instructions written to task_context.md and the agent was nudged to re-read them.";
        }
        log.atInfo().setMessage("agent session respawning").addKeyValue("task", taskId)
                .addKeyValue("alias", task.alias())
                .addKeyValue("cause", "session was down when instructions were relayed")
                .log();
        // A fresh session reads task_context.md on start, so a relay cannot dead-end against a dead agent.
        openTab(taskId, task.alias(), Path.of(task.worktreePath()), configService.load(), false);
        return "Instructions written to task_context.md; the agent session was down, so it was respawned"
                + " to read and follow them.";
    }

    /** One monitor per task id, so relays to one task serialise while different tasks never wait on each other. */
    private Object relayLock(String taskId) {
        return relayLocks.computeIfAbsent(taskId, id -> new Object());
    }

    private String openTab(String taskId, String alias, Path worktreePath, ConfigService.ConfigFile config,
                           boolean planMode) {
        String session = agentSession(config, taskId);
        sessions.openTaskWindow(session, sessions.sessionName(config.viewer().tmuxSession()), taskId,
                alias, worktreePath, planMode);
        return session;
    }

    private String agentSession(ConfigService.ConfigFile config, String taskId) {
        String base = sessions.sessionName(config.viewer().tmuxSession());
        return config.viewer().sharedView()
                ? base
                : base + "-" + TaskName.slug(taskId);
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

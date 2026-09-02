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

    /** What provisioning wrote outside the worktree is the runtime's to undo, and only it knows what that was. */
    public void forgetWorktree(Path worktreePath) {
        agentRuntime.retireWorktree(worktreePath);
    }

    public int killWindows(String taskId) {
        return sessions.killTaskWindows(agentSession(configService.load(), taskId), taskId);
    }

    /** Reserving the viewer is the default: one placed by hand should survive task cycles. */
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
                // Focusing must hand the user a live agent, not the dead prompt left for inspection.
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
            case UNREACHABLE_TAB -> " — the agents viewer is a tab this terminal cannot select; click it"
                    + " yourself";
            case NOT_RUNNING -> " — no agents viewer is open; the session is there, nothing is showing it";
        };
    }

    public String writeTaskContext(String taskId, String instructions) {
        return relay(taskId, instructions);
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
        log.atInfo().setMessage("line said to agent").addKeyValue("task", id)
                .addKeyValue("alias", task.alias()).addKeyValue("said", line).log();
        return "Said to the agent.";
    }

    /**
     * Relays only what the agent has not already been handed, false when the file already holds exactly this brief.
     * A relay NUDGES the session, so an unchanged brief would interrupt the agent every poll interval.
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

    private String relay(String taskId, String instructions) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        Path contextFile = Path.of(task.worktreePath()).resolve("task_context.md");
        // One relay at a time per task: interleaving two writes to one file loses an instruction.
        synchronized (relayLock(taskId)) {
            WorktreeFiles.write(contextFile, instructions);
        }
        // The opening line only: a brief runs to hundreds of lines.
        log.atInfo().setMessage("instructions relayed").addKeyValue("task", taskId)
                .addKeyValue("alias", task.alias())
                .addKeyValue("chars", instructions.length())
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

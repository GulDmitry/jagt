package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.adapter.Executables;

import dev.jagt.orchestrator.port.SessionHost;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.service.WorktreeHooks;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TmuxSessionHost implements SessionHost {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final Processes processRunner;
    private final OrchestratorProperties properties;
    private final OrchestratorPaths paths;
    private final TerminalDriver terminalDriver;
    private final AgentRuntime agentRuntime;
    private final Object lock = new Object();

    @Override
    public String sessionName(String configured) {
        return configured == null || configured.isBlank() ? "jagt" : configured;
    }

    @Override
    public void openTaskWindow(String session, String dedicatedTitle, String taskId, String alias,
                               Path worktreePath, boolean planMode) {
        synchronized (lock) {
            ensureSession(session);
            // One task = one window: respawns must never accumulate duplicates.
            killTaskWindows(session, taskId);
            // After the agent exits the window shows the tail briefly, then closes itself: an interactive
            // shell here would linger forever and read as a hung process.
            String command = WorktreeHooks.gitEnv(worktreePath)
                    + agentRuntime.launchCommand(worktreePath, planMode)
                    + "; printf '\\n[jagt] agent exited — window closes in 15s (Ctrl+C to close now)\\n'; sleep 15";
            // -P -F prints the window id (@N): the only target immune to name collisions when the same task
            // is respawned.
            String windowId = processRunner.run(null, TIMEOUT, List.of(tmux(), "new-window",
                            "-P", "-F", "#{window_id}",
                            "-t", "=" + session + ":", "-n", taskId, "-c", worktreePath.toString(), command))
                    .expectSuccess("tmux new-window " + taskId)
                    .stdout().trim();
            var rename = processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option",
                    "-w", "-t", windowId, "automatic-rename", "off"));
            if (rename.exitCode() != 0) {
                log.atWarn().setMessage("tmux window rename failed")
                        .addKeyValue("task", taskId)
                        .addKeyValue("cause", rename.stderr())
                        .log();
            }
            // Window NAME stays the taskId (findWindowId/killTaskWindows match on it); the alias
            // rides in a window user-option so the terminal title can show "taskId (alias)".
            if (alias != null && !alias.isBlank()) {
                processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option",
                        "-w", "-t", windowId, "@jagt_alias", alias));
            }
            ensureViewer(session, dedicatedTitle);
        }
    }

    @Override
    public boolean focusTaskWindow(String session, String dedicatedTitle, String taskId) {
        synchronized (lock) {
            var windowId = findWindowId(session, taskId);
            if (windowId.isEmpty()) {
                return false;
            }
            processRunner.run(null, TIMEOUT, List.of(tmux(), "select-window", "-t", windowId.get()))
                    .expectSuccess("tmux select-window " + windowId.get());
            ensureViewer(session, dedicatedTitle);
            return true;
        }
    }

    /**
     * Detection is by CHILD PROCESSES: while the agent runs, the pane's shell has it as a child, and a window
     * that outlives its agent is childless. {@code pane_current_command} is useless here — without job control
     * it always reports the shell itself.
     */
    @Override
    public WindowState taskWindowState(String session, String taskId) {
        synchronized (lock) {
            var windowId = findWindowId(session, taskId);
            if (windowId.isEmpty()) {
                return WindowState.MISSING;
            }
            var panePid = processRunner.run(null, TIMEOUT, List.of(tmux(), "display-message",
                    "-p", "-t", windowId.get(), "#{pane_pid}"));
            if (panePid.exitCode() != 0 || panePid.stdout().isBlank()) {
                return WindowState.MISSING;
            }
            var children = processRunner.run(null, TIMEOUT, List.of("pgrep", "-P", panePid.stdout().trim()));
            return children.exitCode() == 0 && !children.stdout().isBlank()
                    ? WindowState.AGENT_RUNNING
                    : WindowState.DEAD_SHELL;
        }
    }

    @Override
    public boolean nudgeTaskWindow(String session, String taskId, String message) {
        synchronized (lock) {
            var windowId = findWindowId(session, taskId);
            if (windowId.isEmpty()) {
                return false;
            }
            processRunner.run(null, TIMEOUT, List.of(tmux(), "send-keys", "-t", windowId.get(), "-l", message))
                    .expectSuccess("tmux send-keys (nudge) " + taskId);
            processRunner.run(null, TIMEOUT, List.of(tmux(), "send-keys", "-t", windowId.get(), "Enter"))
                    .expectSuccess("tmux send-keys Enter " + taskId);
            return true;
        }
    }

    /** Killing a window also kills its processes, so this ends the agent with it. */
    @Override
    public int killTaskWindows(String session, String taskId) {
        synchronized (lock) {
            var windows = processRunner.run(null, TIMEOUT, List.of(tmux(), "list-windows",
                    "-t", "=" + session, "-F", "#{window_id} #{window_name}"));
            if (windows.exitCode() != 0) {
                return 0;
            }
            List<String> ids = windows.stdout().lines()
                    .filter(line -> line.endsWith(" " + taskId))
                    .map(line -> line.substring(0, line.indexOf(' ')))
                    .toList();
            int killed = 0;
            for (String id : ids) {
                var kill = processRunner.run(null, TIMEOUT, List.of(tmux(), "kill-window", "-t", id));
                if (kill.exitCode() == 0) {
                    killed++;
                } else {
                    log.atWarn().setMessage("tmux kill-window failed")
                            .addKeyValue("window", id)
                            .addKeyValue("task", taskId)
                            .addKeyValue("cause", kill.stderr())
                            .log();
                }
            }
            return killed;
        }
    }

    /**
     * Epoch-millis of the window's last terminal output — an agent at work keeps printing even when it makes no
     * MCP call. 0 when the window is gone or unreadable.
     */
    @Override
    public long lastWindowActivityMillis(String session, String taskId) {
        synchronized (lock) {
            var windowId = findWindowId(session, taskId);
            if (windowId.isEmpty()) {
                return 0;
            }
            var r = processRunner.run(null, TIMEOUT, List.of(tmux(), "display-message",
                    "-p", "-t", windowId.get(), "#{window_activity}"));
            if (r.exitCode() != 0 || r.stdout().isBlank()) {
                return 0;
            }
            try {
                return Long.parseLong(r.stdout().trim()) * 1000L;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    private Optional<String> findWindowId(String session, String taskId) {
        var windows = processRunner.run(null, TIMEOUT, List.of(tmux(), "list-windows",
                "-t", "=" + session, "-F", "#{window_id} #{window_name}"));
        if (windows.exitCode() != 0) {
            return Optional.empty();
        }
        return windows.stdout().lines()
                .filter(line -> line.endsWith(" " + taskId))
                .map(line -> line.substring(0, line.indexOf(' ')))
                .findFirst();
    }

    private void ensureSession(String session) {
        var has = processRunner.run(null, TIMEOUT, List.of(tmux(), "has-session", "-t", "=" + session));
        if (has.exitCode() != 0) {
            processRunner.run(null, TIMEOUT, List.of(tmux(), "new-session",
                            "-d", "-s", session, "-c", paths.root().toString()))
                    .expectSuccess("tmux new-session " + session);
        }
        // escape-time 0 removes the ESC delay that makes TUIs feel sluggish. mouse ON so a click on a window
        // name in the status bar switches tasks (the residual lag is the Warp->tmux double render, not the
        // mouse); Shift+Left/Right also switch, and Warp doesn't grab those, unlike Ctrl+b.
        // Re-applied each time; server-global and cheap.
        processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option", "-sg", "escape-time", "0"));
        processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option", "-g", "focus-events", "on"));
        processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option", "-t", session, "mouse", "on"));
        // Drive the terminal (tab) title to the active window name = taskId, so a title-aware
        // terminal like kitty decorates the tab with the current task. Harmless on Warp.
        processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option", "-g", "set-titles", "on"));
        // "taskId (alias)" when the window carries an alias, else just the window name.
        processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option", "-g", "set-titles-string",
                "#{?#{@jagt_alias},#W (#{@jagt_alias}),#W}"));
        // The status-bar window list (what you scan while attached) shows only #I:#W#F by default — add the
        // alias, so "ABC-42 (p1)" is visible in the bar, not just in the terminal tab title. Keep the
        // trailing #F: it renders the window flags (`*` current, `-` last, `Z` zoomed), and dropping it loses
        // the marker on the active window.
        String windowFormat = "#I:#W#{?#{@jagt_alias}, (#{@jagt_alias}),}#F";
        processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option", "-g", "window-status-format", windowFormat));
        processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option", "-g", "window-status-current-format", windowFormat));
        processRunner.run(null, TIMEOUT, List.of(tmux(), "bind-key", "-n", "S-Left", "previous-window"));
        processRunner.run(null, TIMEOUT, List.of(tmux(), "bind-key", "-n", "S-Right", "next-window"));
    }

    /** The viewer must NOT open inside a worktree: `done`/`remove` reap every process whose cwd is under
     *  the removed worktree, which would kill-9 the viewer that first opened there — closing all agents'
     *  tabs. A stable home dir is never a reap target. */
    private static final Path VIEWER_CWD = Path.of(System.getProperty("user.home"));

    private void ensureViewer(String session, String dedicatedTitle) {
        var clients = processRunner.run(null, TIMEOUT, List.of(tmux(), "list-clients", "-t", "=" + session));
        if (clients.exitCode() == 0 && !clients.stdout().isBlank()) {
            return;
        }
        terminalDriver.openViewer(session, dedicatedTitle, VIEWER_CWD);
    }

    /** Resolved HERE, where the process is spawned: a bare name is what the human configured, not a path. */
    private String tmux() {
        return Executables.resolve(properties.tmuxCommand());
    }
}

package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.adapter.Executables;

import dev.jagt.orchestrator.port.SessionHost;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.WorktreeHooks;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class TmuxSessionHost implements SessionHost {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    /** Only what is shown is cut: the full window name stays the task's identity. */
    private static final String SHOWN_NAME = "#{=/10/…:window_name}";
    private static final String EXITED = "[jagt] agent exited";
    private static final int TAIL_LINES = 3;

    private final Processes processRunner;
    private final OrchestratorProperties properties;
    private final OrchestratorPaths paths;
    private final TerminalDriver terminalDriver;
    private final AgentRuntime agentRuntime;
    private final Object lock = new Object();

    @Override
    public Optional<String> unaddressable(String name) {
        return reserved(name);
    }

    /** tmux addresses a window as {@code session:window.pane}, so a name carrying either is unaddressable. */
    static Optional<String> reserved(String name) {
        return name.chars().anyMatch(c -> c == ':' || c == '.')
                ? Optional.of("contains ':' or '.', which tmux reserves") : Optional.empty();
    }

    @Override
    public String sessionName(@Nullable String configured) {
        return configured == null || configured.isBlank() ? "jagt" : configured;
    }

    @Override
    public void openTaskWindow(String session, String dedicatedTitle, String taskId, @Nullable String alias,
                               Path worktreePath, boolean planMode, String model) {
        taskWindow(session, dedicatedTitle, taskId, alias, worktreePath, model.isBlank()
                ? agentRuntime.launchCommand(worktreePath, planMode)
                : agentRuntime.launchCommand(worktreePath, planMode, model));
    }

    @Override
    public void reviveTaskWindow(String session, String dedicatedTitle, String taskId, @Nullable String alias,
                                 Path worktreePath) {
        taskWindow(session, dedicatedTitle, taskId, alias, worktreePath,
                agentRuntime.reviveCommand(worktreePath));
    }

    private void taskWindow(String session, String dedicatedTitle, String taskId, @Nullable String alias,
                            Path worktreePath, String command) {
        synchronized (lock) {
            ensureSession(session);
            // One task = one window: respawns must never accumulate duplicates.
            killTaskWindows(session, taskId);
            String windowId = newWindow(session, taskId, worktreePath,
                    WorktreeHooks.gitEnv(worktreePath) + command);
            // The window name must stay the taskId; the alias rides in a window user-option.
            if (alias != null && !alias.isBlank()) {
                processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option",
                        "-w", "-t", windowId, "@jagt_alias", alias));
            }
            ensureViewer(session, dedicatedTitle);
        }
    }

    @Override
    public void keepFeedWindow(String session, String name, Path file) {
        synchronized (lock) {
            ensureSession(session);
            if (findWindowId(session, name).isEmpty()) {
                newWindow(session, name, VIEWER_CWD,
                        "tail -n 200 -F '" + file.toString().replace("'", "'\\''") + "'");
            }
        }
    }

    /** The window id, which is the only target immune to a name collision on a respawn. */
    private String newWindow(String session, String name, Path cwd, String command) {
        // An interactive shell here would linger forever and read as a hung process.
        // Exec'd, the tail holds no child process, so the window reads as exited while it shows why.
        String ran = command
                + "; printf '\\n" + EXITED + " — window closes in 15s (Ctrl+C to close now)\\n'; exec sleep 15";
        String windowId = processRunner.run(null, TIMEOUT, List.of(tmux(), "new-window",
                        "-P", "-F", "#{window_id}",
                        "-t", "=" + session + ":", "-n", name, "-c", cwd.toString(), ran))
                .expectSuccess("tmux new-window " + name)
                .stdout().trim();
        var rename = processRunner.run(null, TIMEOUT, List.of(tmux(), "set-option",
                "-w", "-t", windowId, "automatic-rename", "off"));
        if (rename.exitCode() != 0) {
            log.atWarn().setMessage("tmux window rename failed")
                    .addKeyValue("window", name)
                    .addKeyValue("cause", OneLine.clipped(rename.stderr()))
                    .log();
        }
        return windowId;
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

    /** {@code pane_current_command} reports the shell without job control, so detection is by child processes. */
    @Override
    public WindowState taskWindowState(String session, String taskId) {
        synchronized (lock) {
            var window = findWindow(session, taskId);
            if (window.isEmpty()) {
                return WindowState.MISSING;
            }
            return agentRunning(window.get()) ? WindowState.AGENT_RUNNING : WindowState.DEAD_SHELL;
        }
    }

    @Override
    public Optional<String> exitTail(String session, String taskId) {
        synchronized (lock) {
            var window = findWindow(session, taskId);
            if (window.isEmpty() || agentRunning(window.get())) {
                return Optional.empty();
            }
            String shown = processRunner.run(null, TIMEOUT, List.of(tmux(), "capture-pane", "-p", "-t",
                    window.get().id())).expectSuccess("tmux capture-pane " + window.get().id()).stdout();
            return printedBeforeExit(shown);
        }
    }

    private static Optional<String> printedBeforeExit(String shown) {
        List<String> lines = shown.lines().toList();
        int exited = IntStream.range(0, lines.size()).filter(i -> lines.get(i).startsWith(EXITED)).max().orElse(-1);
        if (exited < 0) {
            return Optional.empty();
        }
        List<String> printed = lines.subList(0, exited).stream().filter(line -> !line.isBlank()).toList();
        return Optional.of(String.join("\n", printed.subList(Math.max(0, printed.size() - TAIL_LINES), printed.size())));
    }

    private boolean agentRunning(Window window) {
        var children = processRunner.run(null, TIMEOUT, List.of("pgrep", "-P", window.panePid()));
        return children.exitCode() == 0 && !children.stdout().isBlank();
    }

    @Override
    public Optional<String> screenOf(String session, String taskId) {
        synchronized (lock) {
            var windowId = findWindowId(session, taskId);
            if (windowId.isEmpty()) {
                return Optional.empty();
            }
            var screen = processRunner.run(null, TIMEOUT, List.of(tmux(), "capture-pane", "-p", "-e", "-t",
                    windowId.get()));
            return Optional.of(screen.expectSuccess("tmux capture-pane " + windowId.get()).stdout());
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
                            .addKeyValue("cause", OneLine.clipped(kill.stderr()))
                            .log();
                }
            }
            return killed;
        }
    }

    /** Epoch-millis of the window's last terminal output; 0 when the window is gone or unreadable. */
    @Override
    public long lastWindowActivityMillis(String session, String taskId) {
        synchronized (lock) {
            return findWindow(session, taskId).map(window -> window.activitySeconds() * 1000L).orElse(0L);
        }
    }

    private record Window(String id, String panePid, long activitySeconds) {
    }

    private Optional<String> findWindowId(String session, String taskId) {
        return findWindow(session, taskId).map(Window::id);
    }

    private Optional<Window> findWindow(String session, String taskId) {
        var windows = processRunner.run(null, TIMEOUT, List.of(tmux(), "list-windows",
                "-t", "=" + session, "-F", "#{window_id} #{pane_pid} #{window_activity} #{window_name}"));
        if (windows.exitCode() != 0) {
            return Optional.empty();
        }
        return windows.stdout().lines()
                .filter(line -> line.endsWith(" " + taskId))
                .map(line -> line.split(" ", 4))
                .map(fields -> new Window(fields[0], fields[1], Long.parseLong(fields[2])))
                .findFirst();
    }

    private void ensureSession(String session) {
        var has = processRunner.run(null, TIMEOUT, List.of(tmux(), "has-session", "-t", "=" + session));
        if (has.exitCode() != 0) {
            processRunner.run(null, TIMEOUT, List.of(tmux(), "new-session",
                            "-d", "-s", session, "-c", paths.root().toString()))
                    .expectSuccess("tmux new-session " + session);
        }
        // The default is #I:#W#F. Keep the trailing #F: it renders the window flags (`*` current, `-` last,
        // `Z` zoomed), and dropping it loses the marker on the active window.
        String windowFormat = "#I:" + SHOWN_NAME + "#{?#{@jagt_alias}, (#{@jagt_alias}),}#F";
        // One invocation: this runs every few seconds under the lock every other window call waits on.
        var options = processRunner.run(null, TIMEOUT, List.of(tmux(),
                // No ESC delay, which makes TUIs feel sluggish.
                "set-option", "-sg", "escape-time", "0", ";",
                "set-option", "-g", "focus-events", "on", ";",
                "set-option", "-t", session, "mouse", "on", ";",
                // A title-aware terminal decorates its tab with the active window name.
                "set-option", "-g", "set-titles", "on", ";",
                // "taskId (alias)" when the window carries an alias, else just the window name.
                "set-option", "-g", "set-titles-string",
                "#{?#{@jagt_alias}," + SHOWN_NAME + " (#{@jagt_alias})," + SHOWN_NAME + "}", ";",
                "set-option", "-g", "window-status-format", windowFormat, ";",
                "set-option", "-g", "window-status-current-format", windowFormat, ";",
                // Two levels, both lost in tmux's black-on-green default: the bar reads as a bar and not as more
                // pane output, and the active window reads as the active window.
                "set-option", "-g", "status-style", "bg=colour252,fg=colour235", ";",
                "set-option", "-g", "window-status-current-style", "bg=colour160,fg=colour231,bold", ";",
                // Warp does not grab Shift+Left/Right, unlike Ctrl+b.
                "bind-key", "-n", "S-Left", "previous-window", ";",
                "bind-key", "-n", "S-Right", "next-window"));
        // tmux stops a command list at its first error, so one bad option costs the rest.
        if (options.exitCode() != 0) {
            log.atWarn().setMessage("tmux session options failed")
                    .addKeyValue("session", session)
                    .addKeyValue("cause", OneLine.clipped(options.stderr()))
                    .log();
        }
    }

    /** The viewer must NOT open inside a worktree: removing one reaps every process whose cwd is under it, and
     *  a stable home dir is never a reap target. */
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

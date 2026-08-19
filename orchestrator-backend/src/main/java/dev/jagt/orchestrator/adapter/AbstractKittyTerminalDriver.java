package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.TerminalDriver;
import lombok.extern.slf4j.Slf4j;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.StartupCheck;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * kitty as the agents viewer, driven by its remote-control CLI ({@code kitty @ --to unix:<socket> …}) — no
 * keystrokes. Its tabs are addressable, so they can be titled, focused and closed. The tab execs
 * {@code tmux attach}, so agents persist whatever happens to the viewer.
 *
 * <p>One dedicated instance per tmux session, isolated from the user's own kitty by {@code --instance-group}
 * and a per-session socket; their kitty.conf is never touched.
 *
 * <p>OS-neutral except the two hooks below — raising the window ({@link #bringToFront()}) and desktop-only
 * launch options ({@link #platformOptions()}). A new platform is a subclass and nothing else.
 */
@Slf4j
public abstract class AbstractKittyTerminalDriver implements TerminalDriver, StartupCheck {

    protected static final Duration TIMEOUT = Duration.ofSeconds(20);
    /** The window/tab needs a moment to open and attach; don't open a second one meanwhile. */
    private static final long ATTACH_GRACE_MS = 60_000;

    protected final ProcessRunner processRunner;
    protected final String kittyCommand;
    private final OrchestratorProperties properties;
    private final String kittyFontSize;
    private final ConcurrentHashMap<String, Long> openedAt = new ConcurrentHashMap<>();

    protected AbstractKittyTerminalDriver(ProcessRunner processRunner, OrchestratorProperties properties,
                                          String kittyCommand, String kittyFontSize) {
        this.processRunner = processRunner;
        this.properties = properties;
        this.kittyCommand = Executables.resolve(kittyCommand);
        this.kittyFontSize = kittyFontSize;
    }

    @Override
    public List<String> problems() {
        return Executables.unresolved(kittyCommand)
                ? List.of("orchestrator.kitty-command: '" + kittyCommand + "' is not on PATH nor in the usual"
                        + " install directories — nothing would show the agents' sessions. Install kitty, set"
                        + " the key to a full path, or pick another orchestrator.terminal.")
                : List.of();
    }

    /** Extra {@code -o} launch options for this desktop; empty when none are needed. */
    protected abstract List<String> platformOptions();

    @Override
    public void openViewer(String tmuxSession, String dedicatedTitle, Path tabCwd) {
        if (!properties.openWarpWindow()) {
            log.info("Terminal auto-open disabled; attach manually: {} attach -t {}",
                    properties.tmuxCommand(), tmuxSession);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - openedAt.getOrDefault(tmuxSession, 0L) < ATTACH_GRACE_MS) {
            return;
        }
        openedAt.put(tmuxSession, now);

        String socket = socket(tmuxSession);
        String tmux = properties.tmuxCommand();
        if (instanceRunning(socket)) {
            // Add a titled tab that re-attaches (ensureViewer only calls us when detached).
            var launch = processRunner.run(null, TIMEOUT, List.of(kittyCommand, "@", "--to", socket,
                    "launch", "--type=tab", "--tab-title", dedicatedTitle, "--cwd", tabCwd.toString(),
                    "--", tmux, "attach", "-t", tmuxSession));
            if (launch.exitCode() != 0) {
                log.warn("Could not open kitty tab for '{}': {}", tmuxSession, launch.stderr());
            }
            return;
        }
        // First open: launch a dedicated kitty window already attached to the session.
        var open = processRunner.run(null, TIMEOUT, firstOpenCommand(kittyCommand, kittyFontSize, socket,
                dedicatedTitle, tabCwd.toString(), tmux, tmuxSession, platformOptions()));
        if (open.exitCode() != 0) {
            log.warn("Could not launch kitty for tmux session '{}': {}. Attach manually: {} attach -t {}",
                    tmuxSession, open.stderr(), tmux, tmuxSession);
        }
    }

    @Override
    public boolean reveal(String dedicatedTitle) {
        // dedicatedTitle is the base tmux session name, which is also our socket key.
        String socket = socket(dedicatedTitle);
        if (!instanceRunning(socket)) {
            return false;
        }
        processRunner.run(null, TIMEOUT, List.of(kittyCommand, "@", "--to", socket,
                "focus-window", "--match", "cmdline:tmux"));
        bringToFront();
        return true;
    }

    @Override
    public void closeViewerWindow(String dedicatedTitle) {
        // A window-close leaves a headless instance holding the socket on macOS (the app outlives its
        // windows), so kill the dedicated instance by its unique per-session socket path instead — no other
        // process carries it. Agents keep running in tmux; this only detaches the viewer. Best-effort.
        processRunner.run(null, TIMEOUT, List.of("pkill", "-f", socketPath(dedicatedTitle)));
    }

    /**
     * The argv for the first-open kitty window: a dedicated, remote-controllable instance already attached
     * to the tmux session. {@code --detach} forks kitty into the background and returns immediately; without
     * it the GUI process runs in the foreground and ProcessRunner blocks until timeout.
     */
    static List<String> firstOpenCommand(String kittyCommand, String fontSize, String socket, String title,
                                         String directory, String tmux, String tmuxSession,
                                         List<String> platformOptions) {
        List<String> cmd = new ArrayList<>(List.of(kittyCommand, "--detach",
                "--single-instance", "--instance-group", "jagt-" + tmuxSession,
                "--listen-on", socket, "-o", "allow_remote_control=yes"));
        cmd.addAll(platformOptions);
        if (fontSize != null && !fontSize.isBlank()) {
            cmd.addAll(List.of("-o", "font_size=" + fontSize.trim()));
        }
        cmd.addAll(List.of("--title", title, "--directory", directory,
                "--", tmux, "attach", "-t", tmuxSession));
        return List.copyOf(cmd);
    }

    private boolean instanceRunning(String socket) {
        return processRunner.run(null, TIMEOUT, List.of(kittyCommand, "@", "--to", socket, "ls")).exitCode() == 0;
    }

    protected String socket(String tmuxSession) {
        return "unix:" + socketPath(tmuxSession);
    }

    private String socketPath(String tmuxSession) {
        return Path.of(System.getProperty("java.io.tmpdir"), "jagt-kitty-" + tmuxSession).toString();
    }
}

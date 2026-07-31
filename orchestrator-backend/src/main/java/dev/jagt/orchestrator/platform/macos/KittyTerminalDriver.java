package dev.jagt.orchestrator.platform.macos;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.platform.TerminalDriver;
import dev.jagt.orchestrator.service.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * kitty as the agents viewer, driven entirely by its remote-control CLI
 * ({@code kitty @ --to unix:<socket> ...}) — no keystrokes, no URI scheme. Unlike
 * Warp, kitty tabs are addressable: they can be titled, focused and closed
 * programmatically, and its GPU renderer is fast enough that tmux inside it does
 * not feel sluggish. Runs over tmux (the tab execs {@code tmux attach}), so agent
 * persistence is unchanged; only the viewer differs.
 *
 * <p>One dedicated kitty instance per tmux session, isolated from the user's own
 * kitty via {@code --instance-group}/{@code --listen-on} on a per-session socket.
 * Requires kitty's remote control, which we enable at launch with
 * {@code -o allow_remote_control=yes}; no change to the user's kitty.conf needed.
 */
@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "terminal", havingValue = "kitty", matchIfMissing = true)
public class KittyTerminalDriver implements TerminalDriver {

    private static final Logger log = LoggerFactory.getLogger(KittyTerminalDriver.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    /** The window/tab needs a moment to open and attach; don't open a second one meanwhile. */
    private static final long ATTACH_GRACE_MS = 60_000;

    private final ProcessRunner processRunner;
    private final OrchestratorProperties properties;
    private final OsaScript osaScript;
    private final String kittyCommand;
    private final ConcurrentHashMap<String, Long> openedAt = new ConcurrentHashMap<>();

    public KittyTerminalDriver(ProcessRunner processRunner, OrchestratorProperties properties, OsaScript osaScript,
                               @Value("${orchestrator.kitty-command:kitty}") String kittyCommand) {
        this.processRunner = processRunner;
        this.properties = properties;
        this.osaScript = osaScript;
        this.kittyCommand = kittyCommand;
    }

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
        // First open: launch a dedicated kitty window already attached to the session. --detach forks
        // kitty into the background and returns immediately; without it the GUI process runs in the
        // foreground and ProcessRunner blocks until timeout.
        var open = processRunner.run(null, TIMEOUT, List.of(kittyCommand, "--detach",
                "--single-instance", "--instance-group", "jagt-" + tmuxSession,
                "--listen-on", socket, "-o", "allow_remote_control=yes",
                "--title", dedicatedTitle, "--directory", tabCwd.toString(),
                "--", tmux, "attach", "-t", tmuxSession));
        if (open.exitCode() != 0) {
            log.warn("Could not launch kitty for tmux session '{}': {}. Attach manually: {} attach -t {}",
                    tmuxSession, open.stderr(), tmux, tmuxSession);
        }
    }

    @Override
    public void bringToFront() {
        osaScript.run("tell application \"kitty\" to activate");
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
        // macOS keeps the app alive after its windows close, so `close-window` leaves a headless
        // instance holding the socket. Instead kill our dedicated instance by its unique per-session
        // socket path (no other process carries it) — agents keep running in tmux, this only detaches
        // the viewer. Best-effort per the contract.
        processRunner.run(null, TIMEOUT, List.of("pkill", "-f", socketPath(dedicatedTitle)));
    }

    private boolean instanceRunning(String socket) {
        return processRunner.run(null, TIMEOUT, List.of(kittyCommand, "@", "--to", socket, "ls")).exitCode() == 0;
    }

    private String socket(String tmuxSession) {
        return "unix:" + socketPath(tmuxSession);
    }

    private String socketPath(String tmuxSession) {
        return Path.of(System.getProperty("java.io.tmpdir"), "jagt-kitty-" + tmuxSession).toString();
    }
}

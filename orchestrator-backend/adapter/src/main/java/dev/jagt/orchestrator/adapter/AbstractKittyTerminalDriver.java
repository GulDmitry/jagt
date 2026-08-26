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
 * kitty as the agents viewer. Its tabs are addressable, so they can be titled, focused and closed; the tab
 * execs {@code tmux attach}, so agents persist whatever happens to the viewer.
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

    protected abstract List<String> platformOptions();

    @Override
    public void openViewer(String tmuxSession, String dedicatedTitle, Path tabCwd) {
        if (!properties.openWarpWindow()) {
            log.atInfo().setMessage("terminal auto-open disabled")
                    .addKeyValue("fix", properties.tmuxCommand() + " attach -t " + tmuxSession)
                    .log();
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
            var launch = processRunner.run(null, TIMEOUT, List.of(kittyCommand, "@", "--to", socket,
                    "launch", "--type=tab", "--tab-title", dedicatedTitle, "--cwd", tabCwd.toString(),
                    "--", tmux, "attach", "-t", tmuxSession));
            if (launch.exitCode() != 0) {
                log.atWarn().setMessage("kitty tab open failed")
                        .addKeyValue("session", tmuxSession)
                        .addKeyValue("cause", launch.stderr())
                        .log();
            }
            return;
        }
        var open = processRunner.run(null, TIMEOUT, firstOpenCommand(kittyCommand, kittyFontSize, socket,
                dedicatedTitle, tabCwd.toString(), tmux, tmuxSession, platformOptions()));
        if (open.exitCode() != 0) {
            log.atWarn().setMessage("kitty launch failed")
                    .addKeyValue("session", tmuxSession)
                    .addKeyValue("cause", open.stderr())
                    .addKeyValue("fix", tmux + " attach -t " + tmuxSession)
                    .log();
        }
    }

    @Override
    public Revealed reveal(String dedicatedTitle) {
        // dedicatedTitle is the base tmux session name, which is also our socket key.
        String socket = socket(dedicatedTitle);
        if (!instanceRunning(socket)) {
            return Revealed.NOT_RUNNING;
        }
        processRunner.run(null, TIMEOUT, List.of(kittyCommand, "@", "--to", socket,
                "focus-window", "--match", "cmdline:tmux"));
        bringToFront();
        // The viewer gets its own instance, so it is never a tab of somebody else's window; the socket answering
        // at all is the window being there.
        return Revealed.WINDOW;
    }

    @Override
    public void closeViewerWindow(String dedicatedTitle) {
        // A window-close leaves a headless instance holding the socket on macOS (the app outlives its
        // windows), so kill the dedicated instance by its unique per-session socket path instead — no other
        // process carries it. Agents keep running in tmux; this only detaches the viewer.
        processRunner.run(null, TIMEOUT, List.of("pkill", "-f", socketPath(dedicatedTitle)));
    }

    /**
     * {@code --detach} forks kitty into the background and returns immediately; without it the GUI process runs
     * in the foreground and the command blocks until it times out.
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

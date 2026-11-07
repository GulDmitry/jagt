package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.service.OneLine;
import lombok.extern.slf4j.Slf4j;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** kitty's tabs are addressable, so they can be titled, focused and closed; the tab execs {@code tmux attach},
 *  so agents persist whatever happens to the viewer. */
@Slf4j
public abstract class AbstractKittyTerminalDriver implements TerminalDriver, StartupCheck {

    protected static final Duration TIMEOUT = Duration.ofSeconds(20);
    /** The window/tab needs a moment to open and attach; don't open a second one meanwhile. */
    private static final long ATTACH_GRACE_MS = 60_000;

    protected final ProcessRunner processRunner;
    protected final String kittyCommand;
    private final OrchestratorProperties properties;
    private final String kittyFontSize;
    private final Clock clock;
    private final ConcurrentHashMap<String, Long> openedAt = new ConcurrentHashMap<>();

    protected AbstractKittyTerminalDriver(ProcessRunner processRunner, OrchestratorProperties properties,
                                          KittyOptions options, Clock clock) {
        this.processRunner = processRunner;
        this.properties = properties;
        this.kittyCommand = Executables.resolve(options.command());
        this.kittyFontSize = options.fontSize();
        this.clock = clock;
    }

    @Override
    public List<Problem> problems() {
        return Executables.unresolved(kittyCommand)
                ? List.of(new Problem("orchestrator.kitty-command", "orchestrator.kitty-command: '" + kittyCommand
                        + "' is not on PATH nor in the usual install directories — nothing would show the agents'"
                        + " sessions. Install kitty or set the key to a full path."))
                : List.of();
    }

    @Override
    public List<Problem> notes() {
        return Executables.onPath("pkill") ? List.of() : List.of(new Problem("pkill",
                "pkill is not on PATH — a closed viewer's kitty instance keeps running."));
    }

    protected abstract List<String> platformOptions();

    @Override
    public void openViewer(List<String> attachCommand, String dedicatedTitle, Path tabCwd) {
        if (!properties.openTerminalWindow()) {
            log.atInfo().setMessage("terminal auto-open disabled")
                    .addKeyValue("fix", String.join(" ", attachCommand))
                    .log();
            return;
        }
        long now = clock.millis();
        if (now - openedAt.getOrDefault(dedicatedTitle, 0L) < ATTACH_GRACE_MS) {
            return;
        }
        openedAt.put(dedicatedTitle, now);

        String socket = socket(dedicatedTitle);
        if (instanceRunning(socket)) {
            List<String> tabCommand = new ArrayList<>(List.of(kittyCommand, "@", "--to", socket,
                    "launch", "--type=tab", "--tab-title", dedicatedTitle, "--cwd", tabCwd.toString(), "--"));
            tabCommand.addAll(attachCommand);
            var launch = processRunner.run(null, TIMEOUT, tabCommand);
            if (launch.exitCode() != 0) {
                log.atWarn().setMessage("kitty tab open failed")
                        .addKeyValue("title", dedicatedTitle)
                        .addKeyValue("cause", OneLine.clipped(launch.stderr()))
                        .log();
            }
            return;
        }
        var open = processRunner.run(null, TIMEOUT, firstOpenCommand(kittyCommand, kittyFontSize, socket,
                dedicatedTitle, tabCwd.toString(), attachCommand, platformOptions()));
        if (open.exitCode() != 0) {
            log.atWarn().setMessage("kitty launch failed")
                    .addKeyValue("title", dedicatedTitle)
                    .addKeyValue("cause", OneLine.clipped(open.stderr()))
                    .addKeyValue("fix", String.join(" ", attachCommand))
                    .log();
        }
    }

    @Override
    public Revealed reveal(String dedicatedTitle) {
        String socket = socket(dedicatedTitle);
        if (!instanceRunning(socket)) {
            return Revealed.NOT_RUNNING;
        }
        processRunner.run(null, TIMEOUT, List.of(kittyCommand, "@", "--to", socket,
                "focus-window", "--match", "cmdline:tmux"));
        bringToFront();
        // The viewer gets its own instance, so the socket answering at all is the window being there.
        return Revealed.WINDOW;
    }

    @Override
    public void closeViewerWindow(String dedicatedTitle) {
        // On macOS a window-close leaves a headless instance holding the socket, so the dedicated instance is
        // killed by its unique per-session socket path; no other process carries it.
        processRunner.run(null, TIMEOUT, List.of("pkill", "-f", socketPath(dedicatedTitle)));
    }

    /** {@code --detach} forks kitty into the background; without it the command blocks until it times out. */
    static List<String> firstOpenCommand(String kittyCommand, String fontSize, String socket, String title,
                                         String directory, List<String> attach, List<String> platformOptions) {
        List<String> cmd = new ArrayList<>(List.of(kittyCommand, "--detach",
                "--single-instance", "--instance-group", "jagt-" + title,
                "--listen-on", socket, "-o", "allow_remote_control=yes"));
        cmd.addAll(platformOptions);
        if (fontSize != null && !fontSize.isBlank()) {
            cmd.addAll(List.of("-o", "font_size=" + fontSize.trim()));
        }
        cmd.addAll(List.of("--title", title, "--directory", directory, "--"));
        cmd.addAll(attach);
        return List.copyOf(cmd);
    }

    private boolean instanceRunning(String socket) {
        return processRunner.run(null, TIMEOUT, List.of(kittyCommand, "@", "--to", socket, "ls")).exitCode() == 0;
    }

    protected String socket(String dedicatedTitle) {
        return "unix:" + socketPath(dedicatedTitle);
    }

    private String socketPath(String dedicatedTitle) {
        return Path.of(System.getProperty("java.io.tmpdir"), "jagt-kitty-" + dedicatedTitle).toString();
    }
}

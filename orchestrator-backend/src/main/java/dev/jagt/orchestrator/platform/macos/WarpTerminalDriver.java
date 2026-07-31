package dev.jawo.orchestrator.platform.macos;

import dev.jawo.orchestrator.config.OrchestratorProperties;
import dev.jawo.orchestrator.platform.TerminalDriver;
import dev.jawo.orchestrator.service.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warp's only sanctioned scripting surface is the URI scheme. The viewer is a
 * Tab Config (TOML in ~/.warp/tab_configs) opened via warp://tab_config/&lt;name&gt;:
 * it lands as a TAB in the active window and runs `tmux attach` itself — no
 * shell hooks, never keystroke automation. One dedicated agents window is
 * maintained: it is raised by title (addressed System Events window action)
 * before opening a tab; if absent, the tab config opens a new window.
 */
@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "terminal", havingValue = "warp", matchIfMissing = false)
public class WarpTerminalDriver implements TerminalDriver {

    private static final Logger log = LoggerFactory.getLogger(WarpTerminalDriver.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    /** The tab needs seconds to open and attach; don't open a second one meanwhile. */
    private static final long ATTACH_GRACE_MS = 60_000;

    private final ProcessRunner processRunner;
    private final OrchestratorProperties properties;
    private final OsaScript osaScript;
    private final ConcurrentHashMap<String, Long> openedAt = new ConcurrentHashMap<>();

    public WarpTerminalDriver(ProcessRunner processRunner, OrchestratorProperties properties, OsaScript osaScript) {
        this.processRunner = processRunner;
        this.properties = properties;
        this.osaScript = osaScript;
    }

    @Override
    public void openViewer(String tmuxSession, String dedicatedTitle, Path tabCwd) {
        if (!properties.openWarpWindow()) {
            log.info("Warp auto-open disabled; attach manually: {} attach -t {}",
                    properties.tmuxCommand(), tmuxSession);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - openedAt.getOrDefault(tmuxSession, 0L) < ATTACH_GRACE_MS) {
            return;
        }
        openedAt.put(tmuxSession, now);
        writeTabConfig(tmuxSession, tabCwd);
        String uri = raiseDedicatedWindow(dedicatedTitle)
                ? "warp://tab_config/" + tmuxSession
                : "warp://tab_config/" + tmuxSession + "?new_window=true";
        var open = processRunner.run(null, TIMEOUT, List.of("open", uri));
        if (open.exitCode() != 0) {
            log.warn("Could not open Warp tab for tmux session '{}': {}. Attach manually: {} attach -t {}",
                    tmuxSession, open.stderr(), properties.tmuxCommand(), tmuxSession);
        }
    }

    @Override
    public void bringToFront() {
        osaScript.run("tell application \"Warp\" to activate");
    }

    @Override
    public boolean reveal(String dedicatedTitle) {
        boolean raised = raiseDedicatedWindow(dedicatedTitle);
        bringToFront();
        return raised;
    }

    @Override
    public void closeViewerWindow(String dedicatedTitle) {
        try {
            osaScript.run("""
                    tell application "System Events"
                        tell process "Warp"
                            repeat 10 times
                                set found to false
                                repeat with w in windows
                                    if name of w starts with %s then
                                        perform action "AXPress" of button 1 of w
                                        set found to true
                                        delay 0.4
                                        exit repeat
                                    end if
                                end repeat
                                if not found then exit repeat
                            end repeat
                        end tell
                    end tell
                    """.formatted(OsaScript.string(dedicatedTitle)));
        } catch (RuntimeException e) {
            log.warn("Could not close the agents window '{}': {}", dedicatedTitle, e.getMessage());
        }
    }

    /**
     * Raises the agents window (title starts with the tmux session base name —
     * tmux titles the terminal after the attached session). Returns false when
     * no such window exists yet.
     */
    private boolean raiseDedicatedWindow(String dedicatedTitle) {
        try {
            osaScript.run("""
                    tell application "System Events"
                        tell process "Warp"
                            set frontmost to true
                            repeat with w in windows
                                if name of w starts with %s then
                                    perform action "AXRaise" of w
                                    return
                                end if
                            end repeat
                        end tell
                    end tell
                    error "no dedicated window"
                    """.formatted(OsaScript.string(dedicatedTitle)));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void writeTabConfig(String tmuxSession, Path tabCwd) {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".warp", "tab_configs");
            Files.createDirectories(dir);
            String toml = """
                    name = "%s"

                    [[panes]]
                    id = "main"
                    type = "terminal"
                    directory = "%s"
                    commands = ["%s attach -t %s"]
                    """.formatted(tmuxSession, tabCwd, properties.tmuxCommand(), tmuxSession);
            Files.writeString(dir.resolve(tmuxSession + ".toml"), toml);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write Warp tab config for " + tmuxSession, e);
        }
    }
}

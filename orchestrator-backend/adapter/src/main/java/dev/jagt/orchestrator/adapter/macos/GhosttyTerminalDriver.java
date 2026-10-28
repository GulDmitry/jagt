package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.service.OneLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static dev.jagt.orchestrator.adapter.macos.OsaScript.string;

/** Ghostty's windows and tabs are addressable only through its AppleScript dictionary, so this driver is macOS-only. */
@Slf4j
@Component
@ConditionalOnProperty(name = "orchestrator.terminal", havingValue = "ghostty")
public class GhosttyTerminalDriver implements TerminalDriver, StartupCheck {

    /** The window/tab needs a moment to open and attach; don't open a second one meanwhile. */
    private static final long ATTACH_GRACE_MS = 60_000;
    private static final String KEY = "orchestrator.terminal";

    private final OrchestratorProperties properties;
    private final OsaScript osaScript;
    private final Host host;
    private final Clock clock;
    private final ConcurrentHashMap<String, Long> openedAt = new ConcurrentHashMap<>();

    public GhosttyTerminalDriver(OrchestratorProperties properties, OsaScript osaScript, Host host, Clock clock) {
        this.properties = properties;
        this.osaScript = osaScript;
        this.host = host;
        this.clock = clock;
    }

    @Override
    public List<Problem> problems() {
        if (!host.osName().toLowerCase(Locale.ROOT).contains("mac")) {
            return List.of(new Problem(KEY, KEY + ": ghostty is driven through macOS AppleScript, and on '"
                    + host.osName() + "' it has no addressable tabs. Set it to 'kitty'."));
        }
        try {
            osaScript.run("id of application \"Ghostty\"");
            return List.of();
        } catch (RuntimeException e) {
            return List.of(new Problem(KEY, KEY + ": Ghostty 1.3 or newer is not installed — nothing would show the"
                    + " agents' sessions. Install it from https://ghostty.org or set the key to 'kitty'."));
        }
    }

    @Override
    public void openViewer(String tmuxSession, String dedicatedTitle, Path tabCwd) {
        String tmux = Executables.resolve(Objects.requireNonNull(properties.tmuxCommand()));
        String attach = tmux + " attach -t " + tmuxSession;
        if (!properties.openTerminalWindow()) {
            log.atInfo().setMessage("terminal auto-open disabled").addKeyValue("fix", attach).log();
            return;
        }
        long now = clock.millis();
        if (now - openedAt.getOrDefault(tmuxSession, 0L) < ATTACH_GRACE_MS) {
            return;
        }
        openedAt.put(tmuxSession, now);
        try {
            osaScript.run("tell application \"Ghostty\"\n"
                    + "set cfg to new surface configuration from {command:" + string(attach)
                    + ", initial working directory:" + string(tabCwd.toString()) + "}\n"
                    + findTab(dedicatedTitle)
                    + "if hostWindow is missing value then\n"
                    + "set hostWindow to new window with configuration cfg\n"
                    + "set name override of (selected tab of hostWindow) to " + string(dedicatedTitle) + "\n"
                    + "else\n"
                    + "set newTab to new tab in hostWindow with configuration cfg\n"
                    + "set name override of newTab to " + string(dedicatedTitle) + "\n"
                    + "end if\n"
                    + "end tell");
        } catch (RuntimeException e) {
            log.atWarn().setMessage("ghostty tab open failed")
                    .addKeyValue("session", tmuxSession)
                    .addKeyValue("cause", OneLine.clipped(e.getMessage()))
                    .addKeyValue("fix", attach)
                    .log();
        }
    }

    @Override
    public Revealed reveal(String dedicatedTitle) {
        try {
            String answer = osaScript.answer(whenRunning(findTab(dedicatedTitle)
                    + "if hostWindow is missing value then return \"none\"\n"
                    + "activate window hostWindow\n"
                    + "select tab hostTab\n"
                    + "return \"window\"\n"));
            return "window".equals(answer) ? Revealed.WINDOW : Revealed.NOT_RUNNING;
        } catch (RuntimeException e) {
            log.atWarn().setMessage("ghostty reveal failed")
                    .addKeyValue("title", dedicatedTitle)
                    .addKeyValue("cause", OneLine.clipped(e.getMessage()))
                    .log();
            return Revealed.NOT_RUNNING;
        }
    }

    @Override
    public void closeViewerWindow(String dedicatedTitle) {
        try {
            osaScript.run(whenRunning("set doomed to {}\n"
                    + "repeat with w in windows\n"
                    + "repeat with t in tabs of w\n"
                    + "if name override of t is " + string(dedicatedTitle) + " then\n"
                    + "set end of doomed to contents of w\n"
                    + "exit repeat\n"
                    + "end if\n"
                    + "end repeat\n"
                    + "end repeat\n"
                    + "repeat with w in doomed\n"
                    + "close window w\n"
                    + "end repeat\n"));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("ghostty window close failed")
                    .addKeyValue("title", dedicatedTitle)
                    .addKeyValue("cause", OneLine.clipped(e.getMessage()))
                    .log();
        }
    }

    @Override
    public void bringToFront() {
        osaScript.run("tell application \"Ghostty\" to activate");
    }

    private static String findTab(String title) {
        return "set hostWindow to missing value\n"
                + "set hostTab to missing value\n"
                + "repeat with w in windows\n"
                + "if hostWindow is missing value then\n"
                + "repeat with t in tabs of w\n"
                + "if name override of t is " + string(title) + " then\n"
                + "set hostWindow to contents of w\n"
                + "set hostTab to contents of t\n"
                + "exit repeat\n"
                + "end if\n"
                + "end repeat\n"
                + "end if\n"
                + "end repeat\n";
    }

    /** {@code tell} launches an app that is not running, which a reveal or a close must never do. */
    private static String whenRunning(String body) {
        return "if application \"Ghostty\" is not running then return \"none\"\n"
                + "tell application \"Ghostty\"\n" + body + "end tell";
    }
}

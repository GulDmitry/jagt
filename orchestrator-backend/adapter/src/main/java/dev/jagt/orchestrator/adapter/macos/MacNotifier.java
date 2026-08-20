package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * macOS notifications. Prefers `terminal-notifier` when installed: osascript's
 * `display notification` is attributed to Script Editor, so it is silently
 * dropped unless the user has Script Editor notifications enabled — a common,
 * confusing failure. terminal-notifier has its own bundle id and reliably
 * shows a banner — and its banner can carry a CLICK: `-open <url>`, which is
 * the only reason jagt can put the human on the task the banner is about.
 * Falls back to osascript when it isn't installed.
 */
@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "platform", havingValue = "macos", matchIfMissing = true)
@Slf4j
public class MacNotifier implements UserNotifier {

    private static final List<String> TN_CANDIDATES =
            List.of("/opt/homebrew/bin/terminal-notifier", "/usr/local/bin/terminal-notifier");

    private final OsaScript osaScript;
    private final ProcessRunner processRunner;
    private final String terminalNotifier;

    public MacNotifier(OsaScript osaScript, ProcessRunner processRunner) {
        this.osaScript = osaScript;
        this.processRunner = processRunner;
        this.terminalNotifier = TN_CANDIDATES.stream().filter(p -> Files.isExecutable(Path.of(p)))
                .findFirst().orElse(null);
    }

    @Override
    public void notify(String title, String message, String link) {
        try {
            if (terminalNotifier != null) {
                processRunner.run(null, Duration.ofSeconds(10), command(terminalNotifier, title, message, link))
                        .expectSuccess("terminal-notifier");
            } else {
                osaScript.run("display notification " + OsaScript.string(message)
                        + " with title " + OsaScript.string(title));
            }
        } catch (RuntimeException e) {
            // A broken notification must never fail the flow that raised it.
            log.warn("notification failed: {}", e.getMessage());
        }
    }

    /**
     * {@code -open} is what makes the banner clickable; osascript has no equivalent, so a machine without
     * terminal-notifier gets the same words and no click.
     */
    static List<String> command(String terminalNotifier, String title, String message, String link) {
        List<String> command = new java.util.ArrayList<>(List.of(terminalNotifier,
                "-title", title == null ? "jagt" : title, "-message", message == null ? "" : message,
                "-sound", "default"));
        if (link != null) {
            command.addAll(List.of("-open", link));
        }
        return List.copyOf(command);
    }
}

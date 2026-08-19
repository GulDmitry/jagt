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
 * shows a banner. Falls back to osascript when it isn't installed.
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
    public void notify(String title, String message) {
        try {
            if (terminalNotifier != null) {
                processRunner.run(null, Duration.ofSeconds(10), List.of(terminalNotifier,
                                "-title", title, "-message", message, "-sound", "default"))
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
}

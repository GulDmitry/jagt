package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.UserNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Prefers `terminal-notifier`: osascript's `display notification` is attributed to Script Editor, so macOS
 * drops it silently unless the user enabled Script Editor notifications. terminal-notifier is found by name
 * wherever installed, and one that REFUSES the banner falls back to osascript like one that is absent — 3.x
 * asks macOS for permission through its own bundle, and an ad-hoc signature is not one macOS will authorise.
 */
@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "platform", havingValue = "macos", matchIfMissing = true)
@Slf4j
public class MacNotifier implements UserNotifier {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final OsaScript osaScript;
    private final ProcessRunner processRunner;
    private final String terminalNotifier;

    public MacNotifier(OsaScript osaScript, ProcessRunner processRunner,
                       @Value("${orchestrator.terminal-notifier-command:terminal-notifier}")
                       String terminalNotifierCommand) {
        this.osaScript = osaScript;
        this.processRunner = processRunner;
        String resolved = Executables.resolve(terminalNotifierCommand);
        this.terminalNotifier = Executables.unresolved(resolved) ? null : resolved;
    }

    @Override
    public void notify(String title, String message, String link) {
        if (terminalNotifier != null && bannerShown(title, message, link)) {
            return;
        }
        displayNotification(title, message);
    }

    private boolean bannerShown(String title, String message, String link) {
        try {
            processRunner.run(null, TIMEOUT, command(terminalNotifier, title, message, link))
                    .expectSuccess("terminal-notifier");
            return true;
        } catch (RuntimeException e) {
            log.atWarn().setMessage("terminal-notifier failed")
                    .addKeyValue("cause", e.toString())
                    .addKeyValue("effect", "falling back to osascript")
                    .log();
            return false;
        }
    }

    private void displayNotification(String title, String message) {
        try {
            osaScript.run("display notification " + OsaScript.string(message == null ? "" : message)
                    + " with title " + OsaScript.string(title == null ? "jagt" : title));
        } catch (RuntimeException e) {
            // A broken notification must never fail the flow that raised it.
            log.atWarn().setMessage("notification failed")
                    .addKeyValue("cause", e.toString())
                    .log();
        }
    }

    /** {@code -open} is what makes the banner clickable; osascript has no equivalent. */
    static List<String> command(String terminalNotifier, String title, String message, String link) {
        List<String> command = new ArrayList<>(List.of(terminalNotifier,
                "-title", title == null ? "jagt" : title, "-message", message == null ? "" : message,
                "-sound", "default"));
        if (link != null) {
            command.addAll(List.of("-open", link));
        }
        return List.copyOf(command);
    }
}

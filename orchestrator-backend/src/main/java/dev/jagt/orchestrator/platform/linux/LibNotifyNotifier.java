package dev.jagt.orchestrator.platform.linux;

import dev.jagt.orchestrator.platform.UserNotifier;
import dev.jagt.orchestrator.service.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Desktop notifications on Linux via {@code notify-send} (libnotify) — present on every desktop that has a
 * notification daemon, which is what the freedesktop spec makes it. No fallback chain like the macOS
 * notifier needs: {@code notify-send} either reaches the session bus or it does not, and there is no second
 * mechanism that would work where it fails.
 *
 * <p>{@code --app-name jagt} so the banners are attributable (and mutable) as jagt's own, and NORMAL urgency
 * on purpose: every notification jagt sends means "your move", which should persist until seen but must not
 * be a critical alert that ignores do-not-disturb.
 */
@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "platform", havingValue = "linux")
public class LibNotifyNotifier implements UserNotifier {

    private static final Logger log = LoggerFactory.getLogger(LibNotifyNotifier.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ProcessRunner processRunner;
    private final String notifySendCommand;

    public LibNotifyNotifier(ProcessRunner processRunner,
                             @org.springframework.beans.factory.annotation.Value(
                                     "${orchestrator.notify-send-command:notify-send}") String notifySendCommand) {
        this.processRunner = processRunner;
        this.notifySendCommand = notifySendCommand;
    }

    @Override
    public void notify(String title, String message) {
        try {
            processRunner.run(null, TIMEOUT, command(notifySendCommand, title, message))
                    .expectSuccess("notify-send");
        } catch (RuntimeException e) {
            // Contract: a broken notification must never fail the calling flow (watchdog, MCP tools).
            log.warn("notification failed: {}", e.getMessage());
        }
    }

    /**
     * {@code --} before the title: a title or message beginning with a dash would otherwise be parsed as an
     * option by notify-send, and both come from ticket text.
     */
    static List<String> command(String notifySend, String title, String message) {
        return List.of(notifySend, "--app-name", "jagt", "--urgency", "normal", "--",
                title == null ? "jagt" : title, message == null ? "" : message);
    }
}

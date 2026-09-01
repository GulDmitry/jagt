package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.StartupCheck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * There is no fallback chain: {@code notify-send} either reaches the session bus or it does not. The banners
 * are attributable as jagt's own, and NORMAL urgency persists until seen without becoming a critical alert
 * that ignores do-not-disturb.
 */
@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "platform", havingValue = "linux")
@Slf4j
public class LibNotifyNotifier implements UserNotifier, StartupCheck {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ProcessRunner processRunner;
    private final String notifySendCommand;

    public LibNotifyNotifier(ProcessRunner processRunner,
                             @org.springframework.beans.factory.annotation.Value(
                                     "${orchestrator.notify-send-command:notify-send}") String notifySendCommand) {
        this.processRunner = processRunner;
        this.notifySendCommand = Executables.resolve(notifySendCommand);
    }

    @Override
    public List<String> problems() {
        return Executables.unresolved(notifySendCommand)
                ? List.of("orchestrator.notify-send-command: '" + notifySendCommand + "' is not on PATH nor in"
                        + " the usual install directories — every alert jagt raises would go nowhere. Install"
                        + " libnotify or set the key to a full path.")
                : List.of();
    }

    /** The link is dropped: {@code --action} is the only click mechanism, and it needs the process to stay alive
     *  waiting for the daemon to answer. */
    @Override
    public void notify(String title, String message, String link) {
        try {
            processRunner.run(null, TIMEOUT, command(notifySendCommand, title, message))
                    .expectSuccess("notify-send");
        } catch (RuntimeException e) {
            // A broken notification must never fail the flow that raised it.
            log.atWarn().setMessage("notification failed")
                    .addKeyValue("cause", e.toString())
                    .log();
        }
    }

    /** {@code --} before the title: a leading dash would otherwise be parsed as an option. */
    static List<String> command(String notifySend, String title, String message) {
        return List.of(notifySend, "--app-name", "jagt", "--urgency", "normal", "--",
                title == null ? "jagt" : title, message == null ? "" : message);
    }
}

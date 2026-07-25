package dev.jawo.orchestrator.platform.macos;

import dev.jawo.orchestrator.platform.UserNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "platform", havingValue = "macos", matchIfMissing = true)
public class MacNotifier implements UserNotifier {

    private static final Logger log = LoggerFactory.getLogger(MacNotifier.class);

    private final OsaScript osaScript;

    public MacNotifier(OsaScript osaScript) {
        this.osaScript = osaScript;
    }

    @Override
    public void notify(String title, String message) {
        try {
            osaScript.run("display notification " + OsaScript.string(message)
                    + " with title " + OsaScript.string(title));
        } catch (RuntimeException e) {
            // A broken notification must never fail the calling flow (watchdog, tools).
            log.warn("osascript notification failed: {}", e.getMessage());
        }
    }
}

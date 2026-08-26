package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.StartupCheck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The platform is what the human WROTE, and every OS-specific piece is picked from it — so the wrong value
 * starts perfectly and fails where nobody is looking: an alert that cannot be raised is logged, never thrown,
 * because telling a human must not break the flow that wanted to. The machine is the only thing that can
 * contradict the value, so it is asked here.
 */
@Component
public class PlatformCheck implements StartupCheck {

    private final OrchestratorProperties properties;
    private final String osName;

    @Autowired
    public PlatformCheck(OrchestratorProperties properties) {
        this(properties, System.getProperty("os.name", ""));
    }

    PlatformCheck(OrchestratorProperties properties, String osName) {
        this.properties = properties;
        this.osName = osName == null ? "" : osName;
    }

    @Override
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        String running = running();
        String configured = configured();
        if (running == null) {
            problems.add("orchestrator.platform: jagt drives macOS and Linux, and this machine reports '"
                    + osName + "' — there is no platform to set it to.");
        } else if (!running.equals(configured)) {
            problems.add("orchestrator.platform is '" + configured + "' on a machine reporting '" + osName
                    + "': every notification would be handed to a binary this OS does not have, and a failed"
                    + " alert is logged rather than raised, so nothing would tell you a session is blocked."
                    + " Set it to '" + running + "'.");
        }
        return problems;
    }

    /**
     * Unset reads as macOS, the same default the beans this key selects carry. A value that names no platform at
     * all is not answered here and cannot be: nothing is then selected to be asked, and the start is already
     * over before a check could speak.
     */
    private String configured() {
        String platform = properties.platform();
        return platform == null || platform.isBlank() ? "macos" : platform.toLowerCase(Locale.ROOT);
    }

    private String running() {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.contains("linux")) {
            return "linux";
        }
        return name.contains("mac") ? "macos" : null;
    }
}

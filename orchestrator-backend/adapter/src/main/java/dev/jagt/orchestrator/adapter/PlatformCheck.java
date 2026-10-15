package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every OS-specific piece is picked from the configured platform, so a wrong value starts perfectly and fails
 * where nobody is looking. The machine is the only thing that can contradict it, so it is asked here.
 */
@Component
public class PlatformCheck implements StartupCheck {

    private static final String KEY = "orchestrator.platform";

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
    public List<Problem> problems() {
        List<Problem> problems = new ArrayList<>();
        String running = running();
        String configured = configured();
        if (running == null) {
            problems.add(new Problem(KEY, "orchestrator.platform: jagt drives macOS and Linux, and this machine"
                    + " reports '" + osName + "' — there is no platform to set it to."));
        } else if (!running.equals(configured)) {
            problems.add(new Problem(KEY, "orchestrator.platform is '" + configured + "' on a machine reporting '"
                    + osName + "': its notifier binary does not exist here, so no alert would reach you."
                    + " Set it to '" + running + "'."));
        }
        return problems;
    }

    /** Unset reads as macOS, the same default the beans this key selects carry. */
    private String configured() {
        String platform = properties.platform();
        return platform == null || platform.isBlank() ? "macos" : platform.toLowerCase(Locale.ROOT);
    }

    private @Nullable String running() {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.contains("linux")) {
            return "linux";
        }
        return name.contains("mac") ? "macos" : null;
    }
}

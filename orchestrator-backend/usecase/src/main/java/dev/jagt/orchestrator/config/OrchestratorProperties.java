package dev.jagt.orchestrator.config;

import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;


import java.time.Duration;
import java.util.List;

/**
 * Machine/OS-level settings bound from {@code application.yml}, every value verbatim as written: a configured
 * binary name is resolved where it is SPAWNED, not here. Build one with {@link #defaults()} plus {@code withX}.
 */
@ConfigurationProperties(prefix = "orchestrator")
@With
public record OrchestratorProperties(
        String root,
        String configFile,
        String stateFile,
        String platform,
        List<String> editorCommand,
        List<String> editorDiffCommand,
        String agentPrompt,
        String tmuxCommand,
        boolean openTerminalWindow,
        Watchdog watchdog
) {

    public record Watchdog(Duration staleAfter) {
    }

    /** Everything unset but the watchdog threshold, dereferenced on every scan, and {@code openTerminalWindow}. */
    public static OrchestratorProperties defaults() {
        return new OrchestratorProperties(null, null, null, null, null, null, null, null,
                false, new Watchdog(Duration.ofMinutes(5)));
    }
}

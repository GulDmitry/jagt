package dev.jagt.orchestrator.config;

import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;


import java.time.Duration;
import java.util.List;

/**
 * Machine/OS-level settings, bound from {@code application.yml} (see README's Configuration section).
 *
 * <p>Twelve components, so NOBODY calls the canonical constructor by hand: {@link #defaults()} plus the
 * {@code withX} withers are the way in, exactly like {@code ConfigService.ConfigFile}. A row of positional
 * nulls hides which setting a caller actually meant and silently changes meaning if the record is ever
 * reordered.
 */
@ConfigurationProperties(prefix = "orchestrator")
@With
public record OrchestratorProperties(
        String root,
        String configFile,
        String stateFile,
        String platform,
        String terminal,
        List<String> editorCommand,
        List<String> editorDiffCommand,
        String agentPrompt,
        String tmuxCommand,
        boolean openWarpWindow,
        Watchdog watchdog
) {

    /**
     * WHAT THE HUMAN WROTE, VERBATIM. A configured name is looked up on PATH and in the known install locations
     * where it is SPAWNED, not here: resolving it in this record made the configuration depend on the edge that
     * knows those locations, and the two could then only be separated by pulling one of them apart. A bare name
     * is still the right default — defaulting to an absolute install path is how the task flow died on Linux
     * with "Failed to start command" before the agent ever started.
     */
    public record Watchdog(Duration staleAfter) {
    }

    /**
     * Everything unset, except the two things a caller cannot sensibly be missing: the watchdog threshold
     * (dereferenced on every scan — mirrors application.yml's 5m) and {@code openWarpWindow=false}, since
     * opening a terminal window is a visible side effect nobody should get by accident.
     */
    public static OrchestratorProperties defaults() {
        return new OrchestratorProperties(null, null, null, null, null, null, null, null, null,
                false, new Watchdog(Duration.ofMinutes(5)));
    }

    public OrchestratorProperties withStaleAfter(Duration value) {
        return new OrchestratorProperties(root, configFile, stateFile, platform, terminal, editorCommand,
                editorDiffCommand, agentPrompt, tmuxCommand, openWarpWindow,
                new Watchdog(value));
    }
}

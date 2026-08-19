package dev.jagt.orchestrator.config;

import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;


import java.time.Duration;
import java.util.List;

/**
 * Machine/OS-level settings, bound from {@code application.yml} (see README's Configuration section). Every value
 * is WHAT THE HUMAN WROTE, VERBATIM: a configured binary name is resolved where it is SPAWNED, not here, so the
 * configuration does not depend on the edge that knows the install locations. A bare name is still the right
 * default: an absolute install path defaults to one machine's layout and fails to start on every other.
 *
 * <p>NOBODY calls the canonical constructor by hand: {@link #defaults()} plus the {@code withX} withers are the
 * way in. A row of positional nulls hides which setting a caller actually meant, and silently changes meaning if
 * the record is ever reordered.
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

    public record Watchdog(Duration staleAfter) {
    }

    /**
     * Everything unset, except the two things a caller cannot sensibly be missing: the watchdog threshold, which
     * is dereferenced on every scan, and {@code openWarpWindow=false}, since opening a terminal window is a
     * visible side effect nobody should get by accident.
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

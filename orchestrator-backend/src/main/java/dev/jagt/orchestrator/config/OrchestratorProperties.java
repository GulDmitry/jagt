package dev.jagt.orchestrator.config;

import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;

import dev.jagt.orchestrator.platform.Executables;

import java.time.Duration;
import java.util.ArrayList;
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
        String claudeCommand,
        String agentPrompt,
        String tmuxCommand,
        boolean openWarpWindow,
        Watchdog watchdog
) {

    /**
     * The binaries the backend itself spawns are resolved ONCE, here: the configured bare name (the default) is
     * looked up on PATH and then in the known install locations. Defaulting either of these to an absolute
     * install path is how the task flow died on Linux with "Failed to start command" before the agent ever
     * started — see {@link Executables}. For a command list only the launcher is resolved; its arguments are the
     * human's own.
     *
     * <p>The AGENT command is deliberately NOT resolved: it is not spawned by the backend but written into a
     * shell command that runs inside the agent's tmux window, where the human's own PATH applies — and that
     * string is what they read on screen, so an absolute path would only make it noisier.
     */
    public OrchestratorProperties {
        tmuxCommand = Executables.resolve(tmuxCommand);
        editorCommand = withResolvedLauncher(editorCommand);
        editorDiffCommand = withResolvedLauncher(editorDiffCommand);
    }

    private static List<String> withResolvedLauncher(List<String> command) {
        if (command == null || command.isEmpty()) {
            return command;
        }
        List<String> resolved = new ArrayList<>(command);
        resolved.set(0, Executables.resolve(resolved.getFirst()));
        return List.copyOf(resolved);
    }

    public record Watchdog(Duration staleAfter) {
    }

    /**
     * Everything unset, except the two things a caller cannot sensibly be missing: the watchdog threshold
     * (dereferenced on every scan — mirrors application.yml's 5m) and {@code openWarpWindow=false}, since
     * opening a terminal window is a visible side effect nobody should get by accident.
     */
    public static OrchestratorProperties defaults() {
        return new OrchestratorProperties(null, null, null, null, null, null, null, null, null, null,
                false, new Watchdog(Duration.ofMinutes(5)));
    }

    public OrchestratorProperties withStaleAfter(Duration value) {
        return new OrchestratorProperties(root, configFile, stateFile, platform, terminal, editorCommand,
                editorDiffCommand, claudeCommand, agentPrompt, tmuxCommand, openWarpWindow,
                new Watchdog(value));
    }
}

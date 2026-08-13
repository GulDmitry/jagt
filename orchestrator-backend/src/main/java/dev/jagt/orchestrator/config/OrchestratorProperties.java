package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import dev.jagt.orchestrator.platform.Executables;

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
     * tmux is resolved ONCE, here: the configured bare name (the default) is looked up on PATH and then in the
     * known install locations. Its old default was an absolute Homebrew path, so the whole task flow died on
     * Linux with "Failed to start command" before the agent ever started — see {@link Executables}.
     *
     * <p>The AGENT command is deliberately NOT resolved: it is not spawned by the backend but written into a
     * shell command that runs inside the agent's tmux window, where the human's own PATH applies — and that
     * string is what they read on screen, so an absolute path would only make it noisier.
     */
    public OrchestratorProperties {
        tmuxCommand = Executables.resolve(tmuxCommand);
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

    public OrchestratorProperties withRoot(String value) {
        return new OrchestratorProperties(value, configFile, stateFile, platform, terminal, editorCommand,
                editorDiffCommand, claudeCommand, agentPrompt, tmuxCommand, openWarpWindow, watchdog);
    }

    public OrchestratorProperties withConfigFile(String value) {
        return new OrchestratorProperties(root, value, stateFile, platform, terminal, editorCommand,
                editorDiffCommand, claudeCommand, agentPrompt, tmuxCommand, openWarpWindow, watchdog);
    }

    public OrchestratorProperties withStateFile(String value) {
        return new OrchestratorProperties(root, configFile, value, platform, terminal, editorCommand,
                editorDiffCommand, claudeCommand, agentPrompt, tmuxCommand, openWarpWindow, watchdog);
    }

    public OrchestratorProperties withPlatform(String value) {
        return new OrchestratorProperties(root, configFile, stateFile, value, terminal, editorCommand,
                editorDiffCommand, claudeCommand, agentPrompt, tmuxCommand, openWarpWindow, watchdog);
    }

    public OrchestratorProperties withTerminal(String value) {
        return new OrchestratorProperties(root, configFile, stateFile, platform, value, editorCommand,
                editorDiffCommand, claudeCommand, agentPrompt, tmuxCommand, openWarpWindow, watchdog);
    }

    public OrchestratorProperties withEditorCommand(List<String> value) {
        return new OrchestratorProperties(root, configFile, stateFile, platform, terminal, value,
                editorDiffCommand, claudeCommand, agentPrompt, tmuxCommand, openWarpWindow, watchdog);
    }

    public OrchestratorProperties withEditorDiffCommand(List<String> value) {
        return new OrchestratorProperties(root, configFile, stateFile, platform, terminal, editorCommand,
                value, claudeCommand, agentPrompt, tmuxCommand, openWarpWindow, watchdog);
    }

    public OrchestratorProperties withClaudeCommand(String value) {
        return new OrchestratorProperties(root, configFile, stateFile, platform, terminal, editorCommand,
                editorDiffCommand, value, agentPrompt, tmuxCommand, openWarpWindow, watchdog);
    }

    public OrchestratorProperties withAgentPrompt(String value) {
        return new OrchestratorProperties(root, configFile, stateFile, platform, terminal, editorCommand,
                editorDiffCommand, claudeCommand, value, tmuxCommand, openWarpWindow, watchdog);
    }

    public OrchestratorProperties withTmuxCommand(String value) {
        return new OrchestratorProperties(root, configFile, stateFile, platform, terminal, editorCommand,
                editorDiffCommand, claudeCommand, agentPrompt, value, openWarpWindow, watchdog);
    }

    public OrchestratorProperties withOpenWarpWindow(boolean value) {
        return new OrchestratorProperties(root, configFile, stateFile, platform, terminal, editorCommand,
                editorDiffCommand, claudeCommand, agentPrompt, tmuxCommand, value, watchdog);
    }

    public OrchestratorProperties withStaleAfter(Duration value) {
        return new OrchestratorProperties(root, configFile, stateFile, platform, terminal, editorCommand,
                editorDiffCommand, claudeCommand, agentPrompt, tmuxCommand, openWarpWindow,
                new Watchdog(value));
    }
}

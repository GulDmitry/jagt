package dev.jawo.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "orchestrator")
public record OrchestratorProperties(
        String root,
        String configFile,
        String stateFile,
        String platform,
        String terminal,
        List<String> editorCommand,
        String claudeCommand,
        String agentPrompt,
        String tmuxCommand,
        boolean openWarpWindow,
        Watchdog watchdog
) {

    public record Watchdog(Duration staleAfter) {
    }
}

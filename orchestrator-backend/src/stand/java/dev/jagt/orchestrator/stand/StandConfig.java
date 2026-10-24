package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class StandConfig {

    private static final List<String> MARKERS = List.of("jagt.yml.dist", "session-brief.md.dist", "master-brief.md.dist");

    private StandConfig() {
    }

    public static void write(Path root, Path repo, String mcpUrl, String tmuxSession) {
        try {
            for (String marker : MARKERS) {
                Files.copy(Path.of("..", marker), root.resolve(marker), StandardCopyOption.REPLACE_EXISTING);
            }
            Path servers = root.resolve("servers.json");
            Files.writeString(servers, """
                    {"mcpServers":{"stand-host":{"type":"http","url":"%s"}}}
                    """.formatted(mcpUrl));
            Files.writeString(root.resolve("jagt.yml"), jagtYml(repo, servers, tmuxSession));
        } catch (IOException e) {
            throw new StandFailure("cannot write the stand configuration under " + root, e);
        }
    }

    private static String jagtYml(Path repo, Path servers, String tmuxSession) {
        return """
                orchestrator:
                  projects:
                    proj: { path: "%s", baseBranch: origin/main, deployBranch: dev, verifyCommand: ["./check.sh"] }
                  startupChecks: false
                  openTerminalWindow: false
                  viewer: { tmuxSession: "%s", viewMode: shared, keepViewer: false }
                  agent:
                    autoplan: { enabled: false }
                    visualize: { enabled: false }
                    diagnose: { enabled: false }
                  claude: { mcpConfig: "%s" }
                  agentAllowedTools: mcp__stand-host
                  assistant:
                    model: haiku
                    allowedTools: [mcp__stand-host]
                    mcpConfig: "%s"
                  master: { mode: act, mine: [], model: sonnet }
                  autoReview: { enabled: true, minIntervalMinutes: 1, maxIntervalMinutes: 2 }
                  tracker: { mode: "off" }
                """.formatted(repo, tmuxSession, servers, servers);
    }
}

package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jspecify.annotations.Nullable;

public final class StandConfig {

    private StandConfig() {
    }

    public static void write(Path root, Path repo, String mcpUrl, String tmuxSession, StandMode mode,
                             @Nullable Path replay) {
        try {
            try (var shipped = Files.newDirectoryStream(Path.of(".."), "*.dist")) {
                for (Path file : shipped) {
                    Files.copy(file, root.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Path servers = root.resolve("servers.json");
            Files.writeString(servers, """
                    {"mcpServers":{"stand-host":{"type":"http","url":"%s"}}}
                    """.formatted(mcpUrl));
            Files.writeString(root.resolve("jagt.yml"), jagtYml(repo, servers, tmuxSession, mode, replay));
        } catch (IOException e) {
            throw new StandFailure("cannot write the stand configuration under " + root, e);
        }
    }

    private static String jagtYml(Path repo, Path servers, String tmuxSession, StandMode mode, @Nullable Path replay) {
        return """
                orchestrator:
                  projects:
                    proj: { path: "%s", baseBranch: origin/main, deployBranch: dev, verifyCommand: ["./check.sh"] }
                  startupChecks: false
                  openTerminalWindow: false
                  viewer: { tmuxSession: "%s", viewMode: shared, keepViewer: false }
                  agent:
                    cli: %s
                    autoplan: { enabled: false }
                    visualize: { enabled: false }
                    diagnose: { enabled: false }
                  claude: { mcpConfig: "%s" }
                  agentAllowedTools: mcp__stand-host
                  assistant:
                    model: haiku
                    allowedTools: [mcp__stand-host]
                    mcpConfig: "%s"
                  master: { mode: %s, mine: [], model: sonnet }
                  autoReview: { enabled: true, minIntervalMinutes: 1, maxIntervalMinutes: 2 }
                  tracker: { mode: "off" }
                  replay: { file: "%s" }
                """.formatted(repo, tmuxSession, replay == null ? "claude" : "replay", servers, servers, mode.id(),
                replay == null ? "" : replay);
    }
}

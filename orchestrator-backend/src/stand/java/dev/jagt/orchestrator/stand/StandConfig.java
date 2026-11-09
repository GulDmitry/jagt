package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jspecify.annotations.Nullable;

public final class StandConfig {

  private static final Path FLOWS = Path.of("src/stand/resources/flows").toAbsolutePath();

  private StandConfig() {}

  public record Replay(Path recording, String appUrl) {}

  public static void write(
      Path shipped,
      Path root,
      Path repo,
      String mcpUrl,
      String tmuxSession,
      StandMode mode,
      @Nullable Replay replay) {
    try {
      try (var dists = Files.newDirectoryStream(shipped, "*.dist")) {
        for (Path file : dists) {
          Files.copy(file, root.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
      }
      Path roles = shipped.resolve("roles");
      try (var files = Files.walk(roles)) {
        for (Path file :
            (Iterable<Path>)
                files.filter(Files::isRegularFile).filter(candidate -> shipped(roles, candidate))
                    ::iterator) {
          Path target = root.resolve("roles").resolve(roles.relativize(file).toString());
          Files.createDirectories(target.getParent());
          Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
      Path servers = root.resolve("servers.json");
      Files.writeString(
          servers,
          """
          {"mcpServers":{"stand-host":{"type":"http","url":"%s"}}}
          """
              .formatted(mcpUrl));
      Files.writeString(
          root.resolve("jagt.yml"),
          jagtYml(repo, servers, tmuxSession, mode, replay, acceptance(replay)));
    } catch (IOException e) {
      throw new StandFailure("cannot write the stand configuration under " + root, e);
    }
  }

  private static boolean shipped(Path roles, Path file) {
    Path inRole = roles.relativize(file);
    return file.getFileName().toString().endsWith(".dist")
        || inRole.getNameCount() > 2 && "eval".equals(inRole.getName(1).toString());
  }

  private static String jagtYml(
      Path repo,
      Path servers,
      String tmuxSession,
      StandMode mode,
      @Nullable Replay replay,
      String acceptance) {
    return """
    orchestrator:
      %s
      projects:
        proj: { path: "%s", baseBranch: origin/main, deployBranch: dev, verifyCommand: ["./check.sh"] }
      startupChecks: false
      openTerminalWindow: false
      viewer: { tmuxSession: "%s", viewMode: shared, keepViewer: false }
      agent:
        cli: %s
        model: sonnet
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
      %s
    """
        .formatted(
            replay == null ? "" : "codeHost: replay",
            repo,
            tmuxSession,
            replay == null ? "claude" : "replay",
            servers,
            servers,
            mode.id(),
            replay == null ? "" : replay.recording(),
            acceptance);
  }

  private static String acceptance(@Nullable Replay replay) throws IOException {
    if (replay == null
        || !Files.readString(replay.recording()).contains("\"kind\":\"ACCEPTANCE_CHECK\"")) {
      return "";
    }
    return """
    roles: { acceptance: { enabled: true, environments: [{ url: "%s" }], \
    flows: { driver: playwright, path: "%s", command: ["%s"] } } }\
    """
        .formatted(replay.appUrl(), FLOWS, FLOWS.resolve("flows.sh"));
  }
}

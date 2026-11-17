package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jspecify.annotations.Nullable;

public final class StandConfig {

  private static final Path FLOWS = Path.of("src/stand/resources/flows").toAbsolutePath();
  private static final String PASSWORD_SECRET = "${file:/run/secrets/jagt_password_hash}";

  private StandConfig() {}

  public record Replay(Path recording, String appUrl) {}

  public record Target(
      String project, String mcpUrl, String orchestrator, String sandbox, String server) {

    public static Target local(Path repo, String mcpUrl) {
      return new Target("path: \"" + repo + "\"", mcpUrl, "", "", "");
    }

    public static Target container(Path root, String mcpUrl, String publicUrl) {
      return new Target(
          "url: \"file://" + root.resolve("origin.git") + "\"",
          mcpUrl,
          "auth: { mode: password, passwordHash: \""
              + PASSWORD_SECRET
              + "\" }\n"
              + "  usageLimits: { enabled: false }\n"
              + "  platform: linux\n"
              + "  notifier: none\n"
              + "  keepAwake: none\n"
              + "  editor: none",
          "sandbox: { enabled: false }",
          "server: { address: 0.0.0.0, publicUrl: \"" + publicUrl + "\" }");
    }
  }

  public static void write(
      Path shipped,
      Path root,
      Target target,
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
          Path placed = root.resolve("roles").resolve(roles.relativize(file).toString());
          Files.createDirectories(placed.getParent());
          Files.copy(file, placed, StandardCopyOption.REPLACE_EXISTING);
        }
      }
      Path servers = root.resolve("servers.json");
      Files.writeString(
          servers,
          """
          {"mcpServers":{"stand-host":{"type":"http","url":"%s"}}}
          """
              .formatted(target.mcpUrl()));
      Files.writeString(
          root.resolve("jagt.yml"),
          jagtYml(target, servers, tmuxSession, mode, replay, acceptance(root, replay)));
    } catch (IOException e) {
      throw new StandFailure("cannot write the stand configuration under " + root, e);
    }
  }

  private static boolean shipped(Path roles, Path file) {
    Path inRole = roles.relativize(file);
    return file.getFileName().toString().endsWith(".dist")
        || (inRole.getNameCount() > 2 && "eval".equals(inRole.getName(1).toString()));
  }

  private static String jagtYml(
      Target target,
      Path servers,
      String tmuxSession,
      StandMode mode,
      @Nullable Replay replay,
      String acceptance) {
    return """
    orchestrator:
      %s
      projects:
        proj: { %s, baseBranch: origin/main, deployBranch: dev, verifyCommand: ["./check.sh"] }
      startupChecks: false
      terminal: none
      viewer: { tmuxSession: "%s", viewMode: shared, keepViewer: false }
      agent:
        cli: %s
        model: sonnet
        autoplan: { enabled: false }
        visualize: { enabled: false }
        diagnose: { enabled: false }
        %s
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
      %s
    %s
    """
        .formatted(
            replay == null ? "" : "codeHost: replay",
            target.project(),
            tmuxSession,
            replay == null ? "claude" : "replay",
            target.sandbox(),
            servers,
            servers,
            mode.id(),
            replay == null ? "" : replay.recording(),
            acceptance,
            target.orchestrator(),
            target.server());
  }

  private static String acceptance(Path root, @Nullable Replay replay) throws IOException {
    if (replay == null
        || !Files.readString(replay.recording()).contains("\"kind\":\"ACCEPTANCE_CHECK\"")) {
      return "";
    }
    Path flows = Files.createDirectories(root.resolve("flows"));
    try (var files = Files.list(FLOWS)) {
      for (Path file : (Iterable<Path>) files::iterator) {
        Files.copy(
            file,
            flows.resolve(file.getFileName().toString()),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES);
      }
    }
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        """
        environments: [{ url: "%s" }]
        flows: { driver: playwright, path: "%s", command: ["%s"] }
        """
            .formatted(replay.appUrl(), flows, flows.resolve("flows.sh")));
    return "roles: { acceptance: { enabled: true } }";
  }
}

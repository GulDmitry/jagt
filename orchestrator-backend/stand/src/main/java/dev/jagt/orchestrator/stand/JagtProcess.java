package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class JagtProcess implements JagtEngine {

  private static final Path JAR =
      Path.of(System.getProperty("stand.jar", "../build/libs/jagt-run.jar")).toAbsolutePath();
  public static final String BEFORE_RESTART = ".before-restart";
  private static final List<String> OUTPUTS = List.of("jagt.out", "jagt-backend.log");
  private static final Duration STARTUP = Duration.ofMinutes(5);

  private final Path root;
  private final Process process;
  private final int port;
  private final StandBoard board;

  private JagtProcess(Path root, Process process, int port) {
    this.root = root;
    this.process = process;
    this.port = port;
    this.board = new StandBoard(port);
  }

  public static JagtProcess start(Path root, int port) {
    String java = ProcessHandle.current().info().command().orElse("java");
    ProcessBuilder builder =
        new ProcessBuilder(
                List.of(
                    java,
                    "-jar",
                    JAR.toString(),
                    "--server.port=" + port,
                    "--orchestrator.terminal=none",
                    "--logging.level.dev.jagt.orchestrator.surface.mcp.McpProtocolService=DEBUG",
                    "--logging.level.dev.jagt.orchestrator.adapter.assistant.HeadlessClaude=DEBUG"))
            .directory(root.toFile())
            .redirectErrorStream(true)
            .redirectOutput(root.resolve("jagt.out").toFile());
    Map<String, String> env = builder.environment();
    List.of("TMUX", "TMUX_PANE", "ORCHESTRATOR_CONFIG_FILE", "ORCHESTRATOR_STATE_FILE", "LOG_FILE")
        .forEach(env::remove);
    env.keySet().removeIf(name -> name.startsWith("CLAUDE"));
    env.put("ORCHESTRATOR_ROOT", root.toString());
    env.put("ORCHESTRATOR_DATA", root.toString());
    env.put("LOG_FILE", root.resolve("jagt-backend.log").toString());
    env.put("TMUX_TMPDIR", root.resolve("tmux").toString());
    try {
      return new JagtProcess(root, builder.start(), port).awaitReady();
    } catch (IOException e) {
      throw new StandFailure("cannot launch " + JAR + " under " + root, e);
    }
  }

  public static void keepOutput(Path root) {
    for (String name : OUTPUTS) {
      Path file = root.resolve(name);
      if (Files.isRegularFile(file)) {
        try {
          Files.move(
              file, root.resolve(name + BEFORE_RESTART), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
          throw new StandFailure("cannot keep " + file + " before the restart", e);
        }
      }
    }
  }

  @Override
  public long pid() {
    return process.pid();
  }

  @Override
  public int port() {
    return port;
  }

  @Override
  public StandBoard board() {
    return board;
  }

  @Override
  public String windows() {
    return StandShell.windows(root);
  }

  @Override
  public String screens() {
    return StandShell.screens(root);
  }

  @Override
  public JagtEngine restart() {
    close();
    keepOutput(root);
    return start(root, port);
  }

  @Override
  public void close() {
    StandShell.stopTree(process.toHandle());
  }

  private JagtProcess awaitReady() {
    Instant deadline = Instant.now(Clock.systemUTC()).plus(STARTUP);
    while (!board.ready()) {
      if (!process.isAlive() || Instant.now(Clock.systemUTC()).isAfter(deadline)) {
        close();
        throw new StandFailure(
            "jagt did not come up on port " + port + "; read " + root.resolve("jagt.out"));
      }
      pause(Duration.ofSeconds(1));
    }
    return this;
  }

  static void pause(Duration duration) {
    try {
      Thread.sleep(duration); // the interval between two polls of a condition no event announces
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StandFailure("interrupted", e);
    }
  }

  static int freePort() {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new StandFailure("no free port on loopback", e);
    }
  }
}

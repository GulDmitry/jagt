package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.adapter.process.ChildProcesses;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.opentest4j.TestAbortedException;

public final class JagtProcess implements JagtEngine {

  private static final Path JAR =
      Path.of(System.getProperty("stand.jar", "../build/libs/jagt-run.jar")).toAbsolutePath();
  public static final String BEFORE_RESTART = ".before-restart";
  private static final List<String> OUTPUTS = List.of("jagt.out", "jagt-backend.log");
  private static final Duration STARTUP = Duration.ofMinutes(5);

  @FunctionalInterface
  public interface Launcher extends AutoCloseable {
    Process launch(Path root, int port) throws IOException;

    @Override
    default void close() {}
  }

  private static final class Owned implements Launcher {

    private final ChildProcesses children = new ChildProcesses();

    @Override
    public Process launch(Path root, int port) throws IOException {
      return children.spawn(command(root, port));
    }

    @Override
    public void close() {
      children.close();
    }
  }

  private final Path root;
  private final int port;
  private final StandBoard board;
  private final Launcher launcher;
  private final Duration startup;
  private @Nullable Process process;

  public JagtProcess(Path root, int port, StandBoard board, Launcher launcher, Duration startup) {
    this.root = root;
    this.port = port;
    this.board = board;
    this.launcher = launcher;
    this.startup = startup;
  }

  public static JagtProcess start(Path root, int port, StandBoard board) {
    return new JagtProcess(root, port, board, new Owned(), STARTUP).begin();
  }

  public static ProcessBuilder command(Path root, int port) {
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
    return builder;
  }

  public JagtProcess begin() {
    try {
      process = launcher.launch(root, port);
    } catch (IOException e) {
      throw new StandFailure("cannot launch " + JAR + " under " + root, e);
    }
    return awaitReady();
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
    return running().pid();
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
  public Path data() {
    return root;
  }

  @Override
  public StandShell.Result exec(String... command) {
    return StandShell.run(root, List.of(command));
  }

  @Override
  public String log() {
    try {
      return Files.readString(root.resolve("jagt.out"));
    } catch (IOException e) {
      throw new StandFailure("cannot read jagt's output under " + root, e);
    }
  }

  @Override
  public JagtEngine restart(Restart how) {
    if (how == Restart.UPGRADE) {
      throw new TestAbortedException("the host engine runs a jar, so it has no image to upgrade");
    }
    stop();
    if (how == Restart.UNIT) {
      StandShell.killTmux(root);
    }
    keepOutput(root);
    return begin();
  }

  @Override
  public void terminate() {
    stop();
  }

  @Override
  public void close() {
    stop();
    launcher.close();
  }

  private void stop() {
    StandShell.stopTree(running().toHandle());
  }

  private Process running() {
    return Objects.requireNonNull(process, "jagt is not launched");
  }

  private JagtProcess awaitReady() {
    Instant deadline = StandClock.now().plus(startup);
    while (!board.ready()) {
      if (!running().isAlive() || StandClock.now().isAfter(deadline)) {
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

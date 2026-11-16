package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JagtContainer implements JagtEngine {

  private static final Path COMPOSE =
      Path.of("src", "stand", "compose", "compose.stand.yml").toAbsolutePath();
  private static final String TMUX = "TMUX_TMPDIR=/tmp/jagt-tmux";
  private static final String SCREENS =
      "tmux list-windows -a -F '#{session_name}:#{window_index} #{window_name}'"
          + " | while read -r target name; do echo \"=== $target $name\";"
          + " tmux capture-pane -p -S -300 -t \"$target\"; done";
  private static final Duration STARTUP = Duration.ofMinutes(5);

  private final Path root;
  private final int port;
  private final StandBoard board;
  private final StandShell.Runner shell;
  private final Map<String, String> env;

  public JagtContainer(
      Path root, int port, StandBoard board, StandShell.Runner shell, Map<String, String> env) {
    this.root = root;
    this.port = port;
    this.board = board;
    this.shell = shell;
    this.env = env;
  }

  public static JagtContainer start(
      Path root, int port, StandBoard board, Map<String, String> env) {
    JagtContainer container = new JagtContainer(root, port, board, StandShell::run, env);
    StandShell.Result up = container.compose("up", "-d", "--wait", "--pull", "never");
    if (up.exit() != 0) {
      container.close();
      throw new StandFailure("jagt's container did not start under " + root + ": " + up.output());
    }
    return container.awaitReady();
  }

  @Override
  public long pid() {
    String id = compose("ps", "-q", "jagt").output().strip();
    return shell
        .run(root, List.of("docker", "inspect", "-f", "{{.State.Pid}}", id), env)
        .output()
        .lines()
        .map(String::strip)
        .filter(line -> line.matches("\\d+"))
        .findFirst()
        .map(Long::parseLong)
        .orElse(0L);
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
    StandShell.Result windows = exec("tmux", "list-windows", "-a", "-F", "#{window_name}");
    return windows.exit() == 0 ? windows.output().strip().replace('\n', ' ') : "";
  }

  @Override
  public String screens() {
    StandShell.Result screens = exec("sh", "-c", SCREENS);
    return screens.exit() == 0 ? screens.output() : "";
  }

  @Override
  public void logs() {
    try {
      Files.writeString(root.resolve("jagt.out"), compose("logs", "--no-color").output());
    } catch (IOException e) {
      throw new StandFailure("cannot write the container log under " + root, e);
    }
  }

  @Override
  public JagtEngine restart() {
    logs();
    compose("stop");
    JagtProcess.keepOutput(root);
    StandShell.Result start = compose("start");
    if (start.exit() != 0) {
      throw new StandFailure("jagt's container did not restart: " + start.output());
    }
    return awaitReady();
  }

  @Override
  public void close() {
    logs();
    compose("down", "-v", "--remove-orphans");
  }

  private JagtContainer awaitReady() {
    Instant deadline = Instant.now(Clock.systemUTC()).plus(STARTUP);
    while (!board.ready()) {
      if (Instant.now(Clock.systemUTC()).isAfter(deadline)) {
        close();
        throw new StandFailure(
            "jagt did not come up on port " + port + "; read " + root.resolve("jagt.out"));
      }
      JagtProcess.pause(Duration.ofSeconds(1));
    }
    return this;
  }

  private StandShell.Result exec(String... command) {
    List<String> args = new ArrayList<>(List.of("exec", "-T", "-e", TMUX, "jagt"));
    args.addAll(List.of(command));
    return compose(args.toArray(String[]::new));
  }

  private StandShell.Result compose(String... args) {
    List<String> command =
        new ArrayList<>(
            List.of(
                "docker",
                "compose",
                "-f",
                COMPOSE.toString(),
                "-p",
                root.getFileName().toString()));
    command.addAll(List.of(args));
    return shell.run(root, command, env);
  }
}

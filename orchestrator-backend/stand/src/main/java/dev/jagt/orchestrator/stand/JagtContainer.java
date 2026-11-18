package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JagtContainer implements UnitEngine {

  private static final Path COMPOSE =
      Path.of("src", "stand", "compose", "compose.stand.yml").toAbsolutePath();
  private static final String TMUX = "TMUX_TMPDIR=/tmp/jagt-tmux";
  private static final String IMAGE = "jagt-stand";
  private static final String NEXT = "jagt-stand-next";
  private static final Duration STARTUP = Duration.ofMinutes(5);

  private final Path root;
  private final int port;
  private final StandBoard board;
  private final StandShell.Runner shell;
  private final Map<String, String> env;
  private final Duration startup;
  private String image = IMAGE;

  public JagtContainer(
      Path root,
      int port,
      StandBoard board,
      StandShell.Runner shell,
      Map<String, String> env,
      Duration startup) {
    this.root = root;
    this.port = port;
    this.board = board;
    this.shell = shell;
    this.env = env;
    this.startup = startup;
  }

  public static JagtContainer start(
      Path root, int port, StandBoard board, Map<String, String> env) {
    return new JagtContainer(root, port, board, StandShell::run, env, STARTUP).up();
  }

  public JagtContainer up() {
    StandShell.Result up = compose("up", "-d", "--wait", "--pull", "never");
    if (up.exit() != 0) {
      close();
      throw new StandFailure("jagt's container did not start under " + root + ": " + up.output());
    }
    return awaitReady();
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
  public void logs() {
    try {
      Files.writeString(root.resolve("jagt.out"), compose("logs", "--no-color").output());
    } catch (IOException e) {
      throw new StandFailure("cannot write the container log under " + root, e);
    }
  }

  @Override
  public Path data() {
    return root;
  }

  @Override
  public String log() {
    return compose("logs", "--no-color").output();
  }

  @Override
  public JagtEngine restart(Restart how) {
    logs();
    JagtProcess.keepOutput(root);
    switch (how) {
      case JVM -> killJvm();
      case UNIT -> replace(compose("restart"), "restart");
      default -> upgrade();
    }
    return awaitReady();
  }

  @Override
  public void terminate() {
    StandShell.Result killed = compose("kill", "-s", "TERM", "jagt");
    if (killed.exit() != 0) {
      throw new StandFailure("jagt's container did not take SIGTERM: " + killed.output());
    }
    if (!BoardWait.until(
        () -> compose("ps", "--status", "running", "-q").output().isBlank(), startup)) {
      close();
      throw new StandFailure("jagt's container did not exit after SIGTERM; read " + root);
    }
  }

  private void killJvm() {
    StandShell.Result killed = exec("pkill", "-f", JAR);
    if (killed.exit() != 0) {
      throw new StandFailure("jagt's JVM was not running in its container: " + killed.output());
    }
    awaitDown();
  }

  private void upgrade() {
    StandShell.Result tagged = shell.run(root, List.of("docker", "tag", IMAGE, NEXT), env);
    replace(tagged, "tag the new image");
    image = NEXT;
    replace(compose("up", "-d", "--wait", "--pull", "never"), "start the new image");
  }

  private void replace(StandShell.Result result, String what) {
    if (result.exit() != 0) {
      close();
      throw new StandFailure("jagt's container did not " + what + ": " + result.output());
    }
  }

  @Override
  public void close() {
    logs();
    compose("down", "-v", "--remove-orphans");
  }

  private void awaitDown() {
    if (!BoardWait.down(board, startup)) {
      close();
      throw new StandFailure(
          "jagt did not stop on port " + port + "; read " + root.resolve("jagt.out"));
    }
  }

  private JagtContainer awaitReady() {
    if (!BoardWait.up(board, startup, () -> {})) {
      close();
      throw new StandFailure(
          "jagt did not come up on port " + port + "; read " + root.resolve("jagt.out"));
    }
    return this;
  }

  @Override
  public StandShell.Result exec(String... command) {
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
    Map<String, String> withImage = new HashMap<>(env);
    withImage.put("JAGT_IMAGE", image);
    withImage.put("JAGT_USER", owner() + ":0");
    return shell.run(root, command, withImage);
  }

  // The bind-mounted root stays the runner's: a Linux engine keeps container uids, Docker Desktop
  // hides them.
  private String owner() {
    try {
      return Files.getAttribute(root, "unix:uid").toString();
    } catch (IOException | UnsupportedOperationException unread) {
      throw new StandFailure("cannot read the owner of " + root, unread);
    }
  }
}

package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

final class StandLauncher {

  record Place(Path base, Path repo) {}

  private final StandEngine engine;
  private final StandTeardown teardown;
  private final Place place;
  private final Function<Path, Optional<Process>> awake;
  private final Duration poll;

  StandLauncher(
      StandEngine engine,
      StandTeardown teardown,
      Place place,
      Function<Path, Optional<Process>> awake,
      Duration poll) {
    this.engine = engine;
    this.teardown = teardown;
    this.place = place;
    this.awake = awake;
    this.poll = poll;
  }

  static StandLauncher standard() {
    Duration poll = Duration.ofSeconds(1);
    return new StandLauncher(
        StandEngine.configured(),
        new StandTeardown(StandArchive.standard(), StandSlots.LIVE, System.out, poll),
        new Place(
            Path.of(System.getProperty("stand.base", System.getProperty("java.io.tmpdir"))),
            Path.of(System.getProperty("stand.repo", "../.."))),
        StandShell::holdAwake,
        poll);
  }

  StandRun start(String scenario, StandMode mode, @Nullable Path replayed) throws IOException {
    teardown.take();
    try {
      return begin(scenario, mode, replayed);
    } catch (IOException | RuntimeException e) {
      teardown.release();
      throw e;
    }
  }

  private StandRun begin(String scenario, StandMode mode, @Nullable Path replayed)
      throws IOException {
    engine.ready();
    engine.needsRoot(scenario);
    if (replayed == null) {
      engine.needsCredential();
    }
    Path root = newRoot();
    boolean container = engine.image();
    StandMarker marker =
        engine.mark(StandMarker.started(scenario + "/" + mode.id()), root).writeTo(root);
    Files.createDirectories(root.resolve("tmux"));
    Files.createDirectories(root.resolve("home"));
    StandProject project = StandProject.create(root);
    String reach = container ? "host.docker.internal" : "127.0.0.1";
    StandHost host =
        StandHost.listen(
                root.resolve("host"),
                container ? InetAddress.getByName("0.0.0.0") : InetAddress.getLoopbackAddress())
            .start();
    try {
      int port = JagtProcess.freePort();
      String password = container ? UUID.randomUUID().toString() : "";
      StandConfig.Target target =
          container
              ? StandConfig.Target.container(root, host.mcpUrl(reach), "http://127.0.0.1:" + port)
              : StandConfig.Target.local(project.repo(), host.mcpUrl(reach));
      StandConfig.write(
          place.repo(),
          root,
          target,
          root.getFileName().toString(),
          mode,
          replayed == null
              ? null
              : new StandConfig.Replay(
                  placed(root, replayed, host.appUrl(reach)), host.appUrl(reach)));
      if (container) {
        Files.writeString(
            root.resolve("password-hash"), new BCryptPasswordEncoder().encode(password));
        StandShell.run(root, List.of("chmod", "-R", "a+rwX", root.toString()));
      }
      JagtEngine jagt = engine.start(root, port, password);
      return new StandRun(
          new StandSite(root, project, host, awake.apply(root)),
          marker.withJagt(jagt.pid(), jagt.port()).writeTo(root),
          mode,
          jagt,
          replayed,
          teardown,
          poll);
    } catch (RuntimeException e) {
      host.close();
      StandShell.killTmux(root);
      marker.withOutcome(StandMarker.FAILED).writeTo(root);
      throw new StandFailure(
          "stand run "
              + scenario
              + " did not start; kept "
              + root
              + ", evidence in "
              + evidence(root, scenario + "/" + mode.id()),
          e);
    }
  }

  ServerRun server(String scenario) throws IOException {
    teardown.take();
    try {
      return beginServer(scenario);
    } catch (IOException | RuntimeException e) {
      teardown.release();
      throw e;
    }
  }

  private ServerRun beginServer(String scenario) throws IOException {
    engine.ready();
    Path root = newRoot();
    StandMarker marker = engine.mark(StandMarker.started(scenario), root).writeTo(root);
    try {
      int port = JagtProcess.freePort();
      String password = UUID.randomUUID().toString();
      ServerFixtures.write(
          place.repo(),
          new ServerFixtures.Where(
              root, engine.inside(root), engine.insidePort(port), engine.image()),
          password);
      Files.createDirectories(root.resolve("tmux"));
      Files.createDirectories(root.resolve("home"));
      StandShell.run(root, List.of("chmod", "-R", "a+rwX", root.toString()));
      JagtEngine jagt = engine.start(root, port, password);
      return new ServerRun(
          root, marker.withJagt(jagt.pid(), jagt.port()).writeTo(root), jagt, teardown);
    } catch (RuntimeException e) {
      marker.withOutcome(StandMarker.FAILED).writeTo(root);
      throw new StandFailure(
          "server run "
              + scenario
              + " did not start; kept "
              + root
              + ", evidence in "
              + evidence(root, scenario),
          e);
    }
  }

  private static Path evidence(Path root, String scenario) {
    return StandEvidence.target(root, scenario, StandEvidence.DIR.toAbsolutePath());
  }

  private static Path placed(Path root, Path recording, String appUrl) throws IOException {
    return Files.writeString(
        root.resolve("recording.jsonl"), Files.readString(recording).replace("{app}", appUrl));
  }

  private Path newRoot() throws IOException {
    String prefix = StandRoots.PREFIX + ProcessHandle.current().pid() + "-";
    Files.createDirectories(place.base());
    int run = 1;
    while (!created(place.base().resolve(prefix + run))) {
      run++;
    }
    return place.base().resolve(prefix + run).toRealPath();
  }

  private static boolean created(Path root) throws IOException {
    try {
      Files.createDirectory(root);
      return true;
    } catch (FileAlreadyExistsException taken) {
      return false;
    }
  }
}

package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.adapter.process.ChildProcesses;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class KindEngine implements UnitEngine {

  private static final Path MANIFEST =
      Path.of(System.getProperty("stand.repo", "../..")).resolve("examples/kubernetes.yml");
  private static final @Nullable String ARCHIVE = System.getProperty("stand.imageArchive");
  private static final int JAGT_PORT = 8290;
  private static final String BUILT = "jagt-stand";
  private static final String RUN = "jagt-stand:run";
  private static final String NEXT = "jagt-stand:next";
  private static final String DEPLOYMENT = "deployment/jagt";
  private static final Path FIXTURES = Path.of("/data/stand");
  private static final List<String> SEEDED =
      List.of("jagt.yml", "password-hash", "fake-claude.sh", "origin.git");
  private static final Duration LONG = Duration.ofMinutes(10);
  private static final int DELETE_TRIES = 4;
  private static final Duration DELETE_BACKOFF = Duration.ofSeconds(1);
  private static final Duration STARTUP = Duration.ofMinutes(5);

  private static final List<List<String>> PROBES =
      List.of(
          List.of("kubectl", "get", "pod", "-l", "app=jagt", "-o", "yaml"),
          List.of("tmux", "list-sessions"),
          List.of("ps", "-ef"));

  @FunctionalInterface
  public interface Forwarder extends AutoCloseable {
    Process open(Path root, int port) throws IOException;

    @Override
    default void close() {}
  }

  private static final class Owned implements Forwarder {

    private final ChildProcesses children = new ChildProcesses();
    private final Map<String, String> env;

    Owned(Map<String, String> env) {
      this.env = env;
    }

    @Override
    public Process open(Path dir, int local) throws IOException {
      ProcessBuilder builder =
          new ProcessBuilder(
                  "kubectl",
                  "port-forward",
                  "--address",
                  "127.0.0.1",
                  "svc/jagt",
                  local + ":" + JAGT_PORT)
              .directory(dir.toFile())
              .redirectErrorStream(true)
              .redirectOutput(dir.resolve("forward.log").toFile());
      builder.environment().putAll(env);
      return children.spawn(builder);
    }

    @Override
    public void close() {
      children.close();
    }
  }

  private final Path root;
  private final int port;
  private final StandBoard board;
  private final StandShell.Runner shell;
  private final Forwarder forwarder;
  private final Path manifest;
  private final Duration startup;
  private @Nullable Process forward;

  public KindEngine(
      Path root,
      int port,
      StandBoard board,
      StandShell.Runner shell,
      Forwarder forwarder,
      Path manifest,
      Duration startup) {
    this.root = root;
    this.port = port;
    this.board = board;
    this.shell = shell;
    this.forwarder = forwarder;
    this.manifest = manifest;
    this.startup = startup;
  }

  public static KindEngine start(Path root, int port, StandBoard board, Map<String, String> env) {
    Map<String, String> kube = Map.of("KUBECONFIG", root.resolve("kubeconfig").toString());
    StandShell.Runner shell = (cwd, command, ignored) -> StandShell.run(cwd, command, kube, LONG);
    Forwarder forwarder = new Owned(kube);
    return new KindEngine(root, port, board, shell, forwarder, MANIFEST, STARTUP).up();
  }

  public KindEngine up() {
    try {
      ok("create the cluster", "kind", "create", "cluster", "--name", cluster(), "--wait", "180s");
      ok("tag the image", "docker", "tag", BUILT, RUN);
      loadRun();
      Files.writeString(root.resolve("kubernetes.yml"), applied(Files.readString(manifest)));
      Files.writeString(root.resolve("seed.yml"), seedPod());
      ok("apply the manifest", "kubectl", "apply", "-f", root.resolve("kubernetes.yml").toString());
      seed();
      ok("start the pod", "kubectl", "scale", DEPLOYMENT, "--replicas=1");
      return awaitReady();
    } catch (IOException e) {
      throw abandoned(new StandFailure("cannot write the manifests under " + root, e));
    } catch (RuntimeException e) {
      throw abandoned(e);
    }
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
  public Path data() {
    return Path.of("/data");
  }

  @Override
  public int fromOutside(String path, Map<String, String> headers, @Nullable String body) {
    String ip =
        run("kubectl", "get", "pod", "-l", "app=jagt", "-o", "jsonpath={.items[0].status.podIP}")
            .output()
            .strip();
    String base = "http://" + ip + ":" + JAGT_PORT;
    List<String> curl =
        new ArrayList<>(List.of("curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", "-H"));
    curl.add("Origin: " + base);
    headers.forEach((name, value) -> curl.addAll(List.of("-H", name + ": " + value)));
    if (body != null) {
      curl.addAll(List.of("--data", body));
    }
    curl.add(base + path);
    return Integer.parseInt(exec(curl.toArray(String[]::new)).output().strip());
  }

  @Override
  public StandShell.Result exec(String... command) {
    List<String> args = new ArrayList<>(List.of("kubectl", "exec", DEPLOYMENT, "--"));
    args.addAll(List.of(command));
    return run(args.toArray(String[]::new));
  }

  @Override
  public String log() {
    return run("kubectl", "logs", DEPLOYMENT, "--tail=-1").output();
  }

  @Override
  public void logs() {
    try {
      Files.writeString(root.resolve("jagt.out"), log());
    } catch (IOException e) {
      throw new StandFailure("cannot write the pod log under " + root, e);
    }
  }

  @Override
  public JagtEngine restart(Restart how) {
    logs();
    JagtProcess.keepOutput(root);
    capture("before-restart");
    switch (how) {
      case JVM -> killJvm();
      case UNIT -> {
        stopForward();
        ok("delete the pod", "kubectl", "delete", "pod", "-l", "app=jagt", "--wait=true");
      }
      default -> upgrade();
    }
    KindEngine ready = awaitReady();
    capture("after-restart");
    return ready;
  }

  private void capture(String phase) {
    StringBuilder text = new StringBuilder();
    for (List<String> probe : PROBES) {
      try {
        String[] command = probe.toArray(String[]::new);
        StandShell.Result result =
            probe.getFirst().equals("kubectl") ? run(command) : exec(command);
        text.append("=== ").append(String.join(" ", probe)).append(" exit ").append(result.exit());
        text.append('\n').append(result.output()).append('\n');
      } catch (RuntimeException e) {
        StandConsole.line(
            "stand capture failed: phase=" + phase + " probe=" + probe.getFirst() + " cause=" + e);
      }
    }
    try {
      Files.writeString(root.resolve("pod-" + phase + ".txt"), text.toString());
    } catch (IOException e) {
      StandConsole.line("stand capture failed: phase=" + phase + " cause=" + e);
    }
  }

  @Override
  public void terminate() {
    stopForward();
    ok("stop the pod", "kubectl", "scale", DEPLOYMENT, "--replicas=0");
    ok(
        "await the pod's exit",
        "kubectl",
        "wait",
        "--for=delete",
        "pod",
        "-l",
        "app=jagt",
        "--timeout=120s");
  }

  @Override
  public String state() {
    startSeedPod();
    StandShell.Result read = run("kubectl", "exec", "seed", "--", "cat", "/data/jagt/state.json");
    ok("remove the seed pod", "kubectl", "delete", "pod", "seed", "--wait=true");
    if (read.exit() != 0) {
      throw new StandFailure("cannot read state.json in " + cluster() + ": " + read.output());
    }
    return read.output();
  }

  @Override
  public void close() {
    logs();
    usage();
    stopForward();
    forwarder.close();
    StandShell.Result deleted = deleteCluster();
    if (deleted.exit() != 0 && exists()) {
      throw new StandFailure(
          "cluster "
              + cluster()
              + " still exists: kind delete cluster exited "
              + deleted.exit()
              + ": "
              + deleted.output());
    }
  }

  private StandShell.Result deleteCluster() {
    StandShell.Result deleted = run("kind", "delete", "cluster", "--name", cluster());
    for (int attempt = 1; deleted.exit() != 0 && attempt < DELETE_TRIES; attempt++) {
      JagtProcess.pause(DELETE_BACKOFF);
      deleted = run("kind", "delete", "cluster", "--name", cluster());
    }
    return deleted;
  }

  private boolean exists() {
    StandShell.Result listed = run("kind", "get", "clusters");
    return listed.exit() != 0 || listed.output().lines().anyMatch(cluster()::equals);
  }

  private <T extends RuntimeException> T abandoned(T failure) {
    try {
      close();
    } catch (StandFailure left) {
      failure.addSuppressed(left);
    }
    return failure;
  }

  private void killJvm() {
    StandShell.Result killed = exec("pkill", "-f", JAR);
    if (killed.exit() != 0) {
      throw new StandFailure("jagt's JVM was not running in its pod: " + killed.output());
    }
    if (!BoardWait.down(board, startup)) {
      throw abandoned(new StandFailure("jagt did not stop on port " + port + " in " + cluster()));
    }
  }

  private void upgrade() {
    stopForward();
    ok("tag the new image", "docker", "tag", RUN, NEXT);
    load(NEXT);
    ok("set the new image", "kubectl", "set", "image", DEPLOYMENT, "jagt=" + NEXT);
    ok("roll out the new image", "kubectl", "rollout", "status", DEPLOYMENT, "--timeout=300s");
  }

  private void startSeedPod() {
    ok("start the seed pod", "kubectl", "apply", "-f", root.resolve("seed.yml").toString());
    ok(
        "await the seed pod",
        "kubectl",
        "wait",
        "--for=condition=Ready",
        "pod/seed",
        "--timeout=180s");
  }

  private void seed() {
    startSeedPod();
    ok(
        "make the fixtures folder",
        "kubectl",
        "exec",
        "seed",
        "--",
        "mkdir",
        "-p",
        FIXTURES.toString());
    for (String name : SEEDED) {
      ok(
          "copy " + name,
          "kubectl",
          "cp",
          root.resolve(name).toString(),
          "seed:" + FIXTURES.resolve(name));
    }
    ok("remove the seed pod", "kubectl", "delete", "pod", "seed", "--wait=true");
  }

  private static String seedPod() {
    return """
    apiVersion: v1
    kind: Pod
    metadata:
      name: seed
    spec:
      restartPolicy: Never
      terminationGracePeriodSeconds: 0
      securityContext:
        runAsNonRoot: true
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: seed
          image: %s
          command: [sleep, "600"]
          securityContext:
            allowPrivilegeEscalation: false
            capabilities:
              drop: [ALL]
          volumeMounts:
            - name: data
              mountPath: /data
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: jagt-data
    """
        .formatted(RUN);
  }

  private void usage() {
    StandShell.Result peak =
        exec("sh", "-c", "cat /sys/fs/cgroup/memory.peak; grep usage_usec /sys/fs/cgroup/cpu.stat");
    if (peak.exit() == 0) {
      StandConsole.line(
          "stand pod " + cluster() + " memory.peak and cpu: " + peak.output().replace('\n', ' '));
    }
  }

  private String applied(String dist) {
    String text = swap(dist, "registry.example.com/my-jagt:latest", RUN);
    text = swap(text, "  replicas: 1", "  replicas: 0");
    return swap(
        text,
        "  JAGT_PASSWORD_HASH: \"\"",
        """
          JAGT_PASSWORD_HASH: ""
          ORCHESTRATOR_CONFIG_FILE: %s
          GIT_CONFIG_COUNT: "1"
          GIT_CONFIG_KEY_0: safe.directory
          GIT_CONFIG_VALUE_0: "*"
          GIT_AUTHOR_NAME: stand
          GIT_AUTHOR_EMAIL: stand@example.com
          GIT_COMMITTER_NAME: stand
          GIT_COMMITTER_EMAIL: stand@example.com\
        """
            .formatted(FIXTURES.resolve("jagt.yml")));
  }

  private String swap(String text, String from, String to) {
    if (!text.contains(from)) {
      throw new StandFailure(manifest + " no longer holds " + from);
    }
    return text.replace(from, to);
  }

  private void loadRun() {
    if (ARCHIVE != null && Files.exists(Path.of(ARCHIVE))) {
      ok("load " + RUN, "kind", "load", "image-archive", ARCHIVE, "--name", cluster());
    } else {
      load(RUN);
    }
  }

  private void load(String image) {
    ok("load " + image, "kind", "load", "docker-image", image, "--name", cluster());
  }

  private void ok(String what, String... command) {
    long began = System.nanoTime();
    StandShell.Result result = run(command);
    StandConsole.line(
        "stand step "
            + cluster()
            + " "
            + what
            + ": "
            + Duration.ofNanos(System.nanoTime() - began).toMillis()
            + " ms");
    if (result.exit() != 0) {
      throw new StandFailure("cannot " + what + " in " + cluster() + ": " + result.output());
    }
  }

  private StandShell.Result run(String... command) {
    return shell.run(root, List.of(command), Map.of());
  }

  private String cluster() {
    return root.getFileName().toString();
  }

  private void stopForward() {
    if (forward != null) {
      forward.destroy();
      forward = null;
    }
  }

  private KindEngine awaitReady() {
    if (!BoardWait.up(board, startup, this::forwarding)) {
      throw abandoned(
          new StandFailure(
              "jagt did not come up on port " + port + " in " + cluster() + "; read " + root));
    }
    return this;
  }

  private void forwarding() {
    if (forward != null && forward.isAlive()) {
      return;
    }
    try {
      forward = forwarder.open(root, port);
    } catch (IOException e) {
      throw new StandFailure("cannot forward port " + port + " of " + cluster(), e);
    }
  }
}

package dev.jagt.orchestrator.stand;

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
      Path.of(System.getProperty("stand.repo", "../..")).resolve("kubernetes.yml.dist");
  private static final String BUILT = "jagt-stand";
  private static final String RUN = "jagt-stand:run";
  private static final String NEXT = "jagt-stand:next";
  private static final String DEPLOYMENT = "deployment/jagt";
  private static final Path FIXTURES = Path.of("/data/stand");
  private static final List<String> SEEDED =
      List.of("jagt.yml", "password-hash", "fake-claude.sh", "origin.git");
  private static final Duration LONG = Duration.ofMinutes(10);
  private static final Duration STARTUP = Duration.ofMinutes(5);

  @FunctionalInterface
  public interface Forwarder {
    Process open(Path root, int port) throws IOException;
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
    Forwarder forwarder =
        (dir, local) -> {
          ProcessBuilder builder =
              new ProcessBuilder(
                      "kubectl",
                      "port-forward",
                      "--address",
                      "127.0.0.1",
                      "svc/jagt",
                      local + ":8290")
                  .directory(dir.toFile())
                  .redirectErrorStream(true)
                  .redirectOutput(dir.resolve("forward.log").toFile());
          builder.environment().putAll(kube);
          return builder.start();
        };
    return new KindEngine(root, port, board, shell, forwarder, MANIFEST, STARTUP).up();
  }

  public KindEngine up() {
    try {
      ok("create the cluster", "kind", "create", "cluster", "--name", cluster(), "--wait", "180s");
      ok("tag the image", "docker", "tag", BUILT, RUN);
      load(RUN);
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
    switch (how) {
      case JVM -> killJvm();
      case UNIT -> {
        stopForward();
        ok("delete the pod", "kubectl", "delete", "pod", "-l", "app=jagt", "--wait=true");
      }
      default -> upgrade();
    }
    return awaitReady();
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
    StandShell.Result deleted = run("kind", "delete", "cluster", "--name", cluster());
    if (deleted.exit() != 0) {
      throw new StandFailure(
          "cluster "
              + cluster()
              + " still exists: kind delete cluster exited "
              + deleted.exit()
              + ": "
              + deleted.output());
    }
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
      System.out.println(
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

  private void load(String image) {
    ok("load " + image, "kind", "load", "docker-image", image, "--name", cluster());
  }

  private void ok(String what, String... command) {
    StandShell.Result result = run(command);
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

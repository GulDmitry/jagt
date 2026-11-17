package dev.jagt.orchestrator.stand;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.opentest4j.TestAbortedException;

public final class StandEngine {

  public static final String HOST = "host";
  public static final String COMPOSE = "compose";
  public static final String KIND = "kind";

  private static final Path POD_FIXTURES = Path.of("/data/stand");
  private static final int IMAGE_PORT = 8290;

  @FunctionalInterface
  public interface Launch {
    JagtEngine start(Path root, int port, String password, Map<String, String> env);
  }

  private final String engine;
  private final Predicate<String> runs;
  private final Function<String, @Nullable String> environment;
  private final Map<String, Launch> launchers;

  public StandEngine(
      String engine,
      Predicate<String> runs,
      Function<String, @Nullable String> environment,
      Map<String, Launch> launchers) {
    this.engine = engine;
    this.runs = runs;
    this.environment = environment;
    this.launchers = launchers;
  }

  public static StandEngine configured() {
    return new StandEngine(
        System.getProperty("stand.engine", COMPOSE),
        StandEngine::toolRuns,
        System::getenv,
        Map.of(
            HOST,
            (root, port, password, env) ->
                JagtProcess.start(root, port, new StandBoard("http://127.0.0.1:" + port, password)),
            COMPOSE,
            (root, port, password, env) ->
                JagtContainer.start(
                    root, port, new StandBoard("http://127.0.0.1:" + port, password), env),
            KIND,
            (root, port, password, env) ->
                KindEngine.start(
                    root, port, new StandBoard("http://127.0.0.1:" + port, password), env)));
  }

  /**
   * Whether jagt's image is what runs, so the run reads nothing of the host but what is mounted.
   */
  public boolean image() {
    return !HOST.equals(engine);
  }

  /** Whether jagt sees the run's root at the path the host sees it. */
  public boolean sharesRoot() {
    return !KIND.equals(engine);
  }

  public Path inside(Path root) {
    return sharesRoot() ? root : POD_FIXTURES;
  }

  public int insidePort(int port) {
    return image() ? IMAGE_PORT : port;
  }

  public StandMarker mark(StandMarker marker, Path root) {
    String name = root.getFileName().toString();
    return switch (engine) {
      case COMPOSE -> marker.withContainer(name);
      case KIND -> marker.withCluster(name);
      default -> marker;
    };
  }

  public void ready() {
    if (!launchers.containsKey(engine)) {
      throw new StandFailure(
          "unknown stand.engine " + engine + ": " + HOST + ", " + COMPOSE + " or " + KIND);
    }
    for (String tool : tools()) {
      if (!runs.test(tool)) {
        throw new TestAbortedException(
            tool
                + " is absent on this machine, so the stand run is skipped;"
                + " -Pengine=host runs it on the host");
      }
    }
  }

  public void needsRoot(String scenario) {
    if (!sharesRoot()) {
      throw new TestAbortedException(
          scenario
              + " reads the run's root, which the "
              + engine
              + " engine does not share; -Pengine=compose or host runs it");
    }
  }

  public JagtEngine start(Path root, int port, String password) {
    ready();
    Map<String, String> env = new HashMap<>();
    env.put("JAGT_PORT", Integer.toString(port));
    env.put("JAGT_STAND_ROOT", root.toString());
    for (String key : List.of("CLAUDE_CODE_OAUTH_TOKEN", "ANTHROPIC_API_KEY")) {
      Optional.ofNullable(environment.apply(key)).ifPresent(value -> env.put(key, value));
    }
    return Objects.requireNonNull(launchers.get(engine)).start(root, port, password, env);
  }

  private List<String> tools() {
    return switch (engine) {
      case COMPOSE -> List.of("docker");
      case KIND -> List.of("docker", "kind", "kubectl");
      default -> List.of();
    };
  }

  private static boolean toolRuns(String tool) {
    List<String> probe =
        tool.equals("docker")
            ? List.of("docker", "info")
            : List.of("sh", "-c", "command -v " + tool);
    return StandShell.run(Path.of("."), probe).exit() == 0;
  }
}

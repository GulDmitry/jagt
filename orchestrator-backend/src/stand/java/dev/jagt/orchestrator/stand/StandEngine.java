package dev.jagt.orchestrator.stand;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.opentest4j.TestAbortedException;

public final class StandEngine {

  public static final String LOCAL = "local";
  public static final String CONTAINER = "container";

  private final String engine;
  private final BooleanSupplier dockerPresent;

  public StandEngine(String engine, BooleanSupplier dockerPresent) {
    this.engine = engine;
    this.dockerPresent = dockerPresent;
  }

  public boolean container() {
    return CONTAINER.equals(engine);
  }

  public void ready() {
    if (!LOCAL.equals(engine) && !CONTAINER.equals(engine)) {
      throw new StandFailure("unknown stand.engine " + engine + ": " + LOCAL + " or " + CONTAINER);
    }
    if (container() && !dockerPresent.getAsBoolean()) {
      throw new TestAbortedException(
          "docker is absent on this machine, so the stand run is skipped;"
              + " -Plocal runs it on the host");
    }
  }

  public JagtEngine start(Path root, int port, String password) {
    ready();
    if (!container()) {
      return JagtProcess.start(root, port);
    }
    Map<String, String> env = new HashMap<>();
    env.put("JAGT_PORT", Integer.toString(port));
    env.put("JAGT_STAND_ROOT", root.toString());
    for (String key : List.of("CLAUDE_CODE_OAUTH_TOKEN", "ANTHROPIC_API_KEY")) {
      Optional.ofNullable(System.getenv(key)).ifPresent(value -> env.put(key, value));
    }
    return JagtContainer.start(
        root, port, new StandBoard("http://127.0.0.1:" + port, password), env);
  }

  public static boolean dockerRuns() {
    return StandShell.run(Path.of("."), List.of("docker", "info")).exit() == 0;
  }
}

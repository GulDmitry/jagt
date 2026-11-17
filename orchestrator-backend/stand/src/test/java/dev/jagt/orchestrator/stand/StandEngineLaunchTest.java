package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandEngineLaunchTest {

  @Test
  void hostEngineLaunchesJagtOnTheHostAndNeverAnImage(@TempDir Path root) {
    JagtEngine onHost = mock(JagtEngine.class);
    List<Integer> ports = new ArrayList<>();
    StandEngine engine =
        new StandEngine(
            StandEngine.HOST,
            tool -> false,
            name -> null,
            Map.of(
                StandEngine.HOST,
                (dir, port, password, env) -> {
                  ports.add(port);
                  return onHost;
                },
                StandEngine.COMPOSE,
                (dir, port, password, env) -> {
                  throw new StandFailure("container launched");
                }));

    JagtEngine started = engine.start(root, 8123, "");

    assertThat(started).isSameAs(onHost);
    assertThat(ports).containsExactly(8123);
    assertThat(engine.image()).isFalse();
  }

  @Test
  void composeEngineLaunchesJagtInTheContainerWithPasswordAndItsRunEnvironment(@TempDir Path root) {
    JagtEngine inside = mock(JagtEngine.class);
    List<String> passwords = new ArrayList<>();
    List<Map<String, String>> environments = new ArrayList<>();
    StandEngine engine =
        new StandEngine(
            StandEngine.COMPOSE,
            tool -> true,
            name -> name.equals("ANTHROPIC_API_KEY") ? "key-value" : null,
            Map.of(
                StandEngine.HOST,
                (dir, port, password, env) -> {
                  throw new StandFailure("host launched");
                },
                StandEngine.COMPOSE,
                (dir, port, password, env) -> {
                  passwords.add(password);
                  environments.add(env);
                  return inside;
                }));

    JagtEngine started = engine.start(root, 8123, "pw");

    assertThat(started).isSameAs(inside);
    assertThat(passwords).containsExactly("pw");
    assertThat(environments)
        .containsExactly(
            Map.of(
                "JAGT_PORT",
                "8123",
                "JAGT_STAND_ROOT",
                root.toString(),
                "ANTHROPIC_API_KEY",
                "key-value"));
    assertThat(engine.image()).isTrue();
  }

  @Test
  void kindEngineLaunchesJagtInTheCluster(@TempDir Path root) {
    JagtEngine pod = mock(JagtEngine.class);
    StandEngine engine =
        new StandEngine(
            StandEngine.KIND,
            tool -> true,
            name -> null,
            Map.of(StandEngine.KIND, (dir, port, password, env) -> pod));

    JagtEngine started = engine.start(root, 8123, "pw");

    assertThat(started).isSameAs(pod);
  }
}

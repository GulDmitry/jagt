package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StateServiceCompatibilityTest {

  private static final Path FIXTURES = Path.of("src/test/resources/state-compatibility");

  static Stream<Path> releasedStates() throws IOException {
    try (Stream<Path> files = Files.list(FIXTURES)) {
      return files
          .filter(file -> file.getFileName().toString().matches("state-.*\\.json"))
          .sorted()
          .toList()
          .stream();
    }
  }

  @ParameterizedTest
  @MethodSource("releasedStates")
  void keepsEveryTaskStatusAndFieldOfEachReleasedState(Path released, @TempDir Path root)
      throws IOException {
    Files.copy(released, root.resolve("state.json"));
    JsonMapper mapper = new JsonMapper();

    JsonNode readToday = mapper.valueToTree(stateService().withRoot(root).build().read());

    assertThat(readToday).isEqualTo(mapper.readTree(released));
  }

  @Test
  void readsRelease023WithOneTaskPerStatusAndItsConcreteValues(@TempDir Path root)
      throws IOException {
    Files.copy(FIXTURES.resolve("state-0.23.json"), root.resolve("state.json"));

    Map<String, TaskState> tasks = stateService().withRoot(root).build().tasks();
    TaskState inProgress = Objects.requireNonNull(tasks.get("ABC-3"));

    assertThat(tasks.values()).extracting(TaskState::status).containsExactly(TaskStatus.values());
    assertThat(inProgress.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(inProgress.alias()).isEqualTo("alias");
    assertThat(inProgress.title()).isEqualTo("title");
    assertThat(inProgress.repoRequest()).isEqualTo("repoRequest");
    assertThat(inProgress.stop()).extracting(TaskStop::evidence).isEqualTo("evidence");
    assertThat(inProgress.probes()).hasSize(1);
  }
}

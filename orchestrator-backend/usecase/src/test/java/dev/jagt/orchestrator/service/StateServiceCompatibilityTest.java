package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  void keepsEveryTaskAndItsStatusOfEachReleasedMinor(Path released, @TempDir Path root)
      throws IOException {
    Files.copy(released, root.resolve("state.json"));
    JsonMapper mapper = new JsonMapper();

    JsonNode readToday = mapper.valueToTree(stateService().withRoot(root).build().read());

    Map<String, String> statusesToday =
        readToday.get("tasks").properties().stream()
            .collect(toMap(Map.Entry::getKey, task -> task.getValue().get("status").asString()));
    Map<String, String> statusesWritten =
        mapper.readTree(released).get("tasks").properties().stream()
            .collect(toMap(Map.Entry::getKey, task -> task.getValue().get("status").asString()));
    assertThat(statusesToday).hasSize(TaskStatus.values().length).isEqualTo(statusesWritten);
  }

  @ParameterizedTest
  @MethodSource("releasedStates")
  void keepsTheTicketAndRepositoryOfEachReleasedMinor(Path released, @TempDir Path root)
      throws IOException {
    Files.copy(released, root.resolve("state.json"));

    TaskState inProgress =
        Objects.requireNonNull(stateService().withRoot(root).build().tasks().get("ABC-3"));

    assertThat(inProgress.ticket().alias()).isEqualTo("alias");
    assertThat(inProgress.ticket().title()).isEqualTo("title");
    assertThat(inProgress.ticket().ticketUrl()).isEqualTo("ticketUrl");
    assertThat(inProgress.repos().get(0).project()).isEqualTo("project");
  }

  @Test
  void keepsEveryFieldOfRelease023(@TempDir Path root) throws IOException {
    Path released = FIXTURES.resolve("state-0.23.json");
    Files.copy(released, root.resolve("state.json"));
    JsonMapper mapper = new JsonMapper();

    JsonNode readToday = mapper.valueToTree(stateService().withRoot(root).build().read());

    assertThat(readToday).isEqualTo(mapper.readTree(released));
  }

  @Test
  void hasFixtureOfTheMinorBeingBuilt() throws IOException {
    String build = Files.readString(Path.of("../build.gradle"));
    Matcher version = Pattern.compile("(?m)^version = '(\\d+\\.\\d+)\\.\\d+'").matcher(build);
    assertThat(version.find()).isTrue();

    assertThat(FIXTURES.resolve("state-" + version.group(1) + ".json")).isRegularFile();
  }

  @Test
  void readsRelease023WithOneTaskPerStatusAndItsConcreteValues(@TempDir Path root)
      throws IOException {
    Files.copy(FIXTURES.resolve("state-0.23.json"), root.resolve("state.json"));

    Map<String, TaskState> tasks = stateService().withRoot(root).build().tasks();
    TaskState inProgress = Objects.requireNonNull(tasks.get("ABC-3"));

    assertThat(tasks.values())
        .extracting(taskState -> taskState.standing().status())
        .containsExactly(TaskStatus.values());
    assertThat(inProgress.standing().status()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(inProgress.ticket().alias()).isEqualTo("alias");
    assertThat(inProgress.ticket().title()).isEqualTo("title");
    assertThat(inProgress.work().repoRequest()).isEqualTo("repoRequest");
    assertThat(inProgress.work().stop()).extracting(TaskStop::evidence).isEqualTo("evidence");
    assertThat(inProgress.gate().probes()).hasSize(1);
  }
}

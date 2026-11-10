package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class CiWorkflowTest {

  private static final Path WORKFLOW = Path.of("../.github/workflows/ci.yml");

  @Test
  void publishesReleaseOnlyOnceEveryOtherJobIsGreen() {
    Map<String, Map<String, Object>> jobs = jobs();
    Set<String> others =
        jobs.keySet().stream().filter(job -> !job.equals("release")).collect(Collectors.toSet());
    assertThat(Objects.requireNonNull(jobs.get("release")).get("needs"))
        .asInstanceOf(list(String.class))
        .containsExactlyInAnyOrderElementsOf(others);
  }

  @Test
  void publishesFromNoJobButTheRelease() {
    Set<String> publishing =
        jobs().entrySet().stream()
            .filter(job -> job.getValue().toString().contains("gh release"))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    assertThat(publishing).containsExactly("release");
  }

  @Test
  void grantsWritingToTheRepositoryToNoJobButTheRelease() {
    Set<String> writing =
        jobs().entrySet().stream()
            .filter(
                job -> String.valueOf(job.getValue().get("permissions")).contains("contents=write"))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    assertThat(writing).containsExactly("release");
  }

  private static Map<String, Map<String, Object>> jobs() {
    try {
      Map<?, ?> workflow = new Yaml().load(Files.readString(WORKFLOW));
      Map<String, Map<String, Object>> jobs = new LinkedHashMap<>();
      Map<?, ?> declared = (Map<?, ?>) Objects.requireNonNull(workflow.get("jobs"));
      for (Map.Entry<?, ?> job : declared.entrySet()) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Map.Entry<?, ?> field : ((Map<?, ?>) job.getValue()).entrySet()) {
          fields.put((String) field.getKey(), field.getValue());
        }
        jobs.put((String) job.getKey(), fields);
      }
      return jobs;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

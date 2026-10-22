package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

class CiWorkflowTest {

    private static final Path WORKFLOW = Path.of("../.github/workflows/ci.yml");

    @Test
    void publishesAReleaseOnlyOnceEveryOtherJobIsGreen() {
        Map<String, Map<String, Object>> jobs = jobs();
        Set<String> others = jobs.keySet().stream().filter(job -> !job.equals("release")).collect(Collectors.toSet());

        assertThat(jobs.get("release").get("needs")).asInstanceOf(list(String.class))
                .containsExactlyInAnyOrderElementsOf(others);
    }

    @Test
    void publishesFromNoJobButTheRelease() {
        Set<String> publishing = jobs().entrySet().stream()
                .filter(job -> job.getValue().toString().contains("gh release"))
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        assertThat(publishing).containsExactly("release");
    }

    @Test
    void grantsWritingToTheRepositoryToNoJobButTheRelease() {
        Set<String> writing = jobs().entrySet().stream()
                .filter(job -> String.valueOf(job.getValue().get("permissions")).contains("contents=write"))
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        assertThat(writing).containsExactly("release");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> jobs() {
        try {
            Map<String, Object> workflow = new Yaml().load(Files.readString(WORKFLOW));
            return (Map<String, Map<String, Object>>) workflow.get("jobs");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

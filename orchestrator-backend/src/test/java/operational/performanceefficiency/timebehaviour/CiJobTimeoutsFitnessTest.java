package operational.performanceefficiency.timebehaviour;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Direction;
import fitness.core.FitnessFunction;
import fitness.core.Project;
import fitness.core.Threshold;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CiJobTimeoutsFitnessTest {

  private static final FitnessFunction FUNCTION =
      new FitnessFunction(
          "ci-jobs-without-timeout",
          "operational.performance-efficiency.time-behaviour",
          FitnessFunction.FITNESS_ENGINE,
          "count",
          new Threshold(Direction.MAX, 0),
          Map.of(),
          null,
          Map.of());

  @Test
  void namesOneJobWithoutTimeoutAndNeitherTimedJobsNorWorkflowCalls(@TempDir Path repository)
      throws IOException {
    Files.createDirectories(repository.resolve("backend"));
    Files.createDirectories(repository.resolve(".github/workflows"));
    Files.writeString(
        repository.resolve(".github/workflows/ci.yml"),
        """
        jobs:
          timed:
            timeout-minutes: 5
            steps: []
          bare:
            steps: []
          called:
            uses: ./.github/workflows/other.yml
        """);
    Files.writeString(repository.resolve(".gitlab-ci.yml"), "stages: [a]\n");

    var measurement =
        new CiJobTimeoutsFitness()
            .measure(FUNCTION, new Project(repository.resolve("backend"), "", List.of("all")));

    assertThat(measurement.items()).containsExactly("github ci.yml bare");
  }

  @Test
  void namesGitlabJobsWithoutTimeoutUnlessTheDefaultsCarryOne(@TempDir Path repository)
      throws IOException {
    Files.createDirectories(repository.resolve("backend"));
    Files.createDirectories(repository.resolve(".github/workflows"));
    Files.writeString(
        repository.resolve(".gitlab-ci.yml"),
        """
        test:
          stage: test
        heavy:
          stage: heavy
          timeout: 20m
        """);

    var measurement =
        new CiJobTimeoutsFitness()
            .measure(FUNCTION, new Project(repository.resolve("backend"), "", List.of("all")));

    assertThat(measurement.items()).containsExactly("gitlab test");
  }
}

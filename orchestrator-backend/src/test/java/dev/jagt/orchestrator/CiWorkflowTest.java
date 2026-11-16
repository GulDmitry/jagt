package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CiWorkflowTest {

  @Test
  void publishesReleaseOnlyOnceEveryOtherJobIsGreen() {
    Map<String, Map<String, Object>> jobs = CiWorkflow.jobs();
    Set<String> others =
        jobs.keySet().stream()
            .filter(job -> !job.equals("release") && !job.equals("pages"))
            .collect(Collectors.toSet());
    assertThat(Objects.requireNonNull(jobs.get("release")).get("needs"))
        .asInstanceOf(list(String.class))
        .containsExactlyInAnyOrderElementsOf(others);
  }

  @Test
  void publishesFromNoJobButTheRelease() {
    Set<String> publishing =
        CiWorkflow.jobs().entrySet().stream()
            .filter(job -> job.getValue().toString().contains("gh release"))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    assertThat(publishing).containsExactly("release");
  }

  @Test
  void grantsWritingToTheRepositoryToNoJobButTheRelease() {
    Set<String> writing =
        CiWorkflow.jobs().entrySet().stream()
            .filter(
                job -> String.valueOf(job.getValue().get("permissions")).contains("contents=write"))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    assertThat(writing).containsExactly("release");
  }

  @Test
  void runsExactlyTheThreeGroupsOnGithub() {
    assertThat(CiWorkflow.jobs().keySet()).containsExactlyInAnyOrder("test", "heavy", "release");
  }

  @Test
  void runsExactlyTheThreeGroupsOnGitlab() {
    assertThat(CiWorkflow.gitlabJobs()).containsExactlyInAnyOrder("test", "heavy", "release");
  }

  @Test
  void startsOnGithubOnlyOnPushPullRequestScheduleAndTag() {
    assertThat(CiWorkflow.githubTriggers())
        .containsExactlyInAnyOrder("push", "pull_request", "schedule");
  }

  @Test
  void startsOnGitlabOnlyOnPushMergeRequestScheduleAndTag() {
    assertThat(CiWorkflow.gitlabText())
        .contains("$CI_PIPELINE_SOURCE == \"push\"")
        .contains("$CI_PIPELINE_SOURCE == \"merge_request_event\"")
        .contains("$CI_PIPELINE_SOURCE == \"schedule\"")
        .doesNotContain("\"web\"")
        .doesNotContain("\"api\"");
  }

  @Test
  void runsTheHeavyJobOnlyOnScheduleOrTag() {
    assertThat(Objects.requireNonNull(CiWorkflow.jobs().get("heavy")).get("if"))
        .isEqualTo("github.event_name == 'schedule' || startsWith(github.ref, 'refs/tags/v')");
  }

  @Test
  void runsTheTestJobNeverOnSchedule() {
    assertThat(Objects.requireNonNull(CiWorkflow.jobs().get("test")).get("if"))
        .isEqualTo("github.event_name != 'schedule'");
  }
}

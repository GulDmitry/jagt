package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.plan.Probe;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.stop.RefusalKind;
import dev.jagt.orchestrator.task.usage.AgentSpend;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TaskStateTest {

  @Test
  void readsStateFileWrittenBeforeTaskCouldSpanRepositories() {
    String legacy =
        """
        {
          "project": "demo",
          "worktreePath": "/wt/ABC-1-demo",
          "status": "CI_POLLING",
          "lastActiveTimestamp": 1700000000000,
          "message": "MR: https://host/mr/9",
          "alias": "a1",
          "remoteUrl": "git@host:g/p.git",
          "title": "Widget layout is off",
          "mrUrl": "https://host/mr/9",
          "deployCommit": "cafebabe1234",
          "mrCreatedAt": 1700000000000,
          "lastPolledAt": 0
        }\
        """;

    TaskState task = new JsonMapper().readValue(legacy, TaskState.class);

    assertThat(task.repos()).hasSize(1);
    assertThat(task.project()).isEqualTo("demo");
    assertThat(task.worktreePath()).isEqualTo("/wt/ABC-1-demo");
    assertThat(task.remoteUrl()).isEqualTo("git@host:g/p.git");
    assertThat(task.mrUrl()).isEqualTo("https://host/mr/9");
    assertThat(
            Objects.requireNonNull(task.work().deployment()).targets().getFirst().landing().merge())
        .isEqualTo("cafebabe1234");
    assertThat(task.standing().status()).isEqualTo(TaskStatus.CI_POLLING);
    assertThat(task.ticket().alias()).isEqualTo("a1");
  }

  @Test
  void writesRepositoriesAsListAndReadsThemBack() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("php", "/wt/ABC-1-php"), TaskRepo.of("java", "/wt/ABC-1-java")),
                TaskStatus.IN_PROGRESS)
            .alias("a1")
            .title("contract change")
            .build();
    JsonMapper mapper = new JsonMapper();

    String json = mapper.writeValueAsString(task);
    TaskState reread = mapper.readValue(json, TaskState.class);

    assertThat(json)
        .contains("\"repos\"")
        .doesNotContain("\"worktreePath\":\"/wt/ABC-1-php\",\"project\"");
    assertThat(reread.projects()).containsExactly("php", "java");
    assertThat(reread.worktreePath()).isEqualTo("/wt/ABC-1-php");
  }

  @Test
  void listsOnlyTheRepositoriesThatCarryProjectName() {
    TaskState task =
        TaskState.builder(
                List.of(
                    TaskRepo.of("php", "/wt/php"),
                    TaskRepo.of(null, "/wt/x"),
                    TaskRepo.of(" ", "/wt/y")),
                TaskStatus.IN_PROGRESS)
            .build();

    assertThat(task.projects()).containsExactly("php");
  }

  @Test
  void readsBackEveryFieldItWroteIncludingWhatAnEarlierFileCouldOmit() {
    JsonMapper mapper = new JsonMapper();
    TaskState written =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withChecksUnread(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"))
            .withProbes(List.of(Probe.specified(Probe.Kind.COMMAND, List.of("make"), 0)))
            .refused(RefusalKind.GIT_STATE);

    TaskState read = mapper.readValue(mapper.writeValueAsString(written), TaskState.class);

    assertThat(read.checks().checksUnread())
        .containsExactly(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"));
    assertThat(read.gate().probes()).hasSize(1);
    assertThat(read.gate().refusals()).containsEntry(RefusalKind.GIT_STATE, 1);
    assertThat(read.standing().history()).hasSize(1);
  }

  @Test
  void recordsWhatJagtFoundAboutTaskWithoutCountingItAsTheAgentMoving() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).lastActiveTimestamp(1_000).build();
    AgentSpend spend = AgentSpend.NONE.plus(TokenUsage.ofCall(10, 0, 5, 0), "log", 7);
    GitTrust trust = GitTrust.of(Map.of("main", "abc"));
    List<Probe> probes = List.of(Probe.specified(Probe.Kind.REF, List.of("main"), 0));

    TaskState found =
        task.withAgentSpend(spend)
            .withGitTrust(trust)
            .withProbes(probes)
            .withApproved(true)
            .withChecksUnread(new ChecksUnread(1_000, ""));

    assertThat(found.gate().agentSpend()).isEqualTo(spend);
    assertThat(found.gate().gitTrust()).isEqualTo(trust);
    assertThat(found.gate().probes()).isEqualTo(probes);
    assertThat(found.round().approved()).isTrue();
    assertThat(found.checks().unread()).isTrue();
    assertThat(found.standing().lastActiveTimestamp()).isEqualTo(1_000);
  }

  @Test
  void movesThePrimaryToTheNextRepositoryWhenThePrimaryLeaves() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt-api"), TaskRepo.of("web", "/wt-web")),
                TaskStatus.IN_PROGRESS)
            .build();

    assertThat(task.withoutRepo("api").project()).isEqualTo("web");
  }

  @Test
  void keepsTheOnlyRepositoryOfTheTask() {
    TaskState task = TaskState.builder("api", "/wt-api", TaskStatus.IN_PROGRESS).build();

    assertThat(task.withoutRepo("api").projects()).containsExactly("api");
  }

  @Test
  void holdsNoRepositoryUntilProjectIsRegistered() {
    assertThat(
            TaskState.builder(List.of(TaskRepo.of(null, "/scratch/ABC-1")), TaskStatus.NEW)
                .build()
                .unplaced())
        .isTrue();
    assertThat(TaskState.builder("api", "/wt", TaskStatus.NEW).build().unplaced()).isFalse();
  }

  @Test
  void holdsNoRequestForProjectWhenTheTaskHasNoRepository() {
    TaskState task = TaskState.builder(null, null, TaskStatus.IN_PROGRESS).build();

    assertThat(task.reviewRequestOf("api")).isEmpty();
  }
}

package dev.jagt.orchestrator.service.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.Generation;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TestProjectConfigBuilder;
import dev.jagt.orchestrator.service.git.WorktreeInspection;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class MergedRequestsTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {});

  private final ConfigService config = mock(ConfigService.class);
  private final WorktreeInspection inspection = mock(WorktreeInspection.class);
  private final RepoDeparture departure = mock(RepoDeparture.class);

  private final TaskState reviewed =
      TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).mrUrl("http://mr/1").build();

  @TempDir Path root;

  private StateService state;
  private MergedRequests requests;

  @BeforeEach
  void readsMergedRequestsOverTheTempState() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString()),
                FakeHost.machine()),
            reducer,
            event -> {});
    requests =
        new MergedRequests(
            state, config, inspection, new WorktreeChanges(config, inspection), departure);
    when(config.project("proj"))
        .thenReturn(new ProjectConfig("/srv/proj", "origin/main", "dev", List.of()));
    when(inspection.mergedIntoTarget(any(), any(), any())).thenReturn(false);
  }

  @Test
  void requestReadsNotMergedAgainWhenGitNoLongerFindsItsHeadInTheTarget() {
    state.putTask("ABC-42", reviewed.withMerged("proj", true));

    boolean none = requests.refresh("ABC-42");

    assertThat(none).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().requestsMerged()).isFalse();
  }

  @Test
  void primaryRepositoryHoldingNoWorkLeavesOnceAnotherHoldsRequest() {
    TaskRepo api = TaskRepo.of("api", "/wt-api");
    when(config.project("api"))
        .thenReturn(new ProjectConfig("/srv/api", "origin/main", "dev", List.of()));
    state.putTask(
        "ABC-42",
        TaskState.builder(
                List.of(api, new TaskRepo("proj", "/wt", null, "http://mr/1")), TaskStatus.REVIEWED)
            .build());

    requests.refresh("ABC-42");

    verify(departure).leaveWorkless("ABC-42", api, "it holds no work");
  }

  @Test
  void repositoryHoldingWorkStaysWhereAnotherHoldsRequest() {
    when(config.project("api"))
        .thenReturn(new ProjectConfig("/srv/api", "origin/main", "dev", List.of()));
    when(inspection.hasUncommittedChanges(any(), any())).thenReturn(true);
    state.putTask(
        "ABC-42",
        TaskState.builder(
                List.of(
                    TaskRepo.of("api", "/wt-api"),
                    new TaskRepo("proj", "/wt", null, "http://mr/1")),
                TaskStatus.REVIEWED)
            .build());

    requests.refresh("ABC-42");

    verifyNoInteractions(departure);
  }

  @Test
  void repositoryHoldingNoWorkStaysWhereNoRepositoryHoldsRequest() {
    state.putTask(
        "ABC-42",
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt-api"), TaskRepo.of("proj", "/wt")),
                TaskStatus.IN_PROGRESS)
            .build());

    requests.refresh("ABC-42");

    verifyNoInteractions(departure);
  }

  @Test
  void requestStaysMergedWhereTheHostSaidSoAndGitSeesNoMergeCommit() {
    state.putTask("ABC-42", reviewed.withMergedByHost("proj"));

    boolean none = requests.refresh("ABC-42");

    assertThat(none).isTrue();
  }

  @Test
  void requestStaysMergedWhereTheHostSaidMergedAndNothingElseIsKnown() {
    state.putTask("ABC-42", reviewed);

    requests.hostSaidMerged("ABC-42", "proj", new Generation(Generation.Kind.ROUND, "http://mr/1"));

    assertThat(state.task("ABC-42").orElseThrow().requestsMerged()).isTrue();
  }

  @Test
  void dropsTheHostsWordReadBeforeTheNextRoundLinkedItsRequest() {
    state.putTask("ABC-42", reviewed);

    requests.hostSaidMerged("ABC-42", "proj", new Generation(Generation.Kind.ROUND, "http://mr/0"));

    assertThat(state.task("ABC-42").orElseThrow().requestsMerged()).isFalse();
  }

  @Test
  void dropsWhatGitFoundWhereTheNextRoundLinkedItsRequestDuringTheRead() {
    state.putTask("ABC-42", reviewed);
    when(inspection.mergedIntoTarget(any(), any(), any()))
        .thenAnswer(call -> {
          state.putTask(
              "ABC-42",
              TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).mrUrl("http://mr/2").build());
          return true;
        });

    requests.refresh("ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().requestsMerged()).isFalse();
  }

  @Test
  void requestStaysMergedWhenGitCannotBeRead() {
    state.putTask("ABC-42", reviewed.withMerged("proj", true));
    when(inspection.mergedIntoTarget(any(), any(), any()))
        .thenThrow(new IllegalStateException("fetch failed"));

    boolean none = requests.refresh("ABC-42");

    assertThat(none).isTrue();
  }

  @Test
  void owesRequestToHostedRepositoryWithNoneAndNotToOneWithNoCodeHost() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    java.util.Map.of(
                        "api", new ProjectConfig("/api", "origin/main", "dev", List.of()),
                        "web",
                            TestProjectConfigBuilder.projectConfig()
                                .withPath("/web")
                                .withBaseBranch("main")
                                .withCodeHost("none")
                                .build(),
                        "docs", new ProjectConfig("/docs", "origin/main", "dev", List.of()))));
    when(config.project("api"))
        .thenReturn(new ProjectConfig("/api", "origin/main", "dev", List.of()));
    when(inspection.hasUncommittedChanges(any(), any())).thenReturn(true);
    List<String> owing =
        requests.owingRequest(
            TaskState.builder(
                    List.of(
                        TaskRepo.of("api", "/wt-api"),
                        TaskRepo.of("web", "/wt-web"),
                        new TaskRepo("docs", "/wt-docs", null, "http://mr/docs")),
                    TaskStatus.CI_POLLING)
                .build());

    assertThat(owing).containsExactly("api");
  }

  @Test
  void owesNoRequestForHostedRepositoryHoldingNothingToShip() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    java.util.Map.of(
                        "api", new ProjectConfig("/api", "origin/main", "dev", List.of()))));
    when(config.project("api"))
        .thenReturn(new ProjectConfig("/api", "origin/main", "dev", List.of()));

    List<String> owing =
        requests.owingRequest(
            TaskState.builder(List.of(TaskRepo.of("api", "/wt-api")), TaskStatus.CI_POLLING)
                .build());

    assertThat(owing).isEmpty();
  }

  @Test
  void repositoryWhoseRequestTheHostClosedLeavesTheTaskWhereTheDepartureTakesIt() {
    TaskRepo web = new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web");
    when(departure.leave("ABC-42", web, "its request was closed on the host")).thenReturn(true);

    boolean left = requests.hostSaidClosed("ABC-42", web);

    assertThat(left).isTrue();
  }

  @Test
  void repositoryTheDepartureKeepsStaysWhoseRequestTheHostClosed() {
    TaskRepo primary = new TaskRepo("api", "/api-wt", "git@host:g/api.git", "http://mr/api");
    when(departure.leave("ABC-42", primary, "its request was closed on the host"))
        .thenReturn(false);

    boolean left = requests.hostSaidClosed("ABC-42", primary);

    assertThat(left).isFalse();
  }
}

package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class MergedRequestsTest {

  private final ConfigService config = mock(ConfigService.class);
  private final WorktreeInspection inspection = mock(WorktreeInspection.class);
  private final RepoDeparture departure = mock(RepoDeparture.class);

  @Test
  void requestReadsNotMergedAgainWhenGitNoLongerFindsItsHeadInTheTarget(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .mrUrl("http://mr/1")
            .build()
            .withMerged("proj", true));
    when(config.project("proj"))
        .thenReturn(new ProjectConfig("/srv/proj", "origin/main", "dev", List.of()));
    when(inspection.mergedIntoTarget(any(), any(), any())).thenReturn(false);

    boolean none =
        new MergedRequests(
                state, config, inspection, new WorktreeChanges(config, inspection), departure)
            .refresh("ABC-42");

    assertThat(none).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().requestsMerged()).isFalse();
  }

  @Test
  void requestStaysMergedWhereTheHostSaidSoAndGitSeesNoMergeCommit(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .mrUrl("http://mr/1")
            .build()
            .withMergedByHost("proj"));
    when(config.project("proj"))
        .thenReturn(new ProjectConfig("/srv/proj", "origin/main", "dev", List.of()));
    when(inspection.mergedIntoTarget(any(), any(), any())).thenReturn(false);

    boolean none =
        new MergedRequests(
                state, config, inspection, new WorktreeChanges(config, inspection), departure)
            .refresh("ABC-42");

    assertThat(none).isTrue();
  }

  @Test
  void requestStaysMergedWhereTheHostSaidMergedAndNothingElseIsKnown(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).mrUrl("http://mr/1").build());

    new MergedRequests(
            state, config, inspection, new WorktreeChanges(config, inspection), departure)
        .hostSaidMerged("ABC-42", "proj");

    assertThat(state.task("ABC-42").orElseThrow().requestsMerged()).isTrue();
  }

  @Test
  void requestStaysMergedWhenGitCannotBeRead(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .mrUrl("http://mr/1")
            .build()
            .withMerged("proj", true));
    when(config.project("proj"))
        .thenReturn(new ProjectConfig("/srv/proj", "origin/main", "dev", List.of()));
    when(inspection.mergedIntoTarget(any(), any(), any()))
        .thenThrow(new IllegalStateException("fetch failed"));

    boolean none =
        new MergedRequests(
                state, config, inspection, new WorktreeChanges(config, inspection), departure)
            .refresh("ABC-42");

    assertThat(none).isTrue();
  }

  @Test
  void owesARequestToAHostedRepositoryWithNoneAndNotToOneWithNoCodeHost(@TempDir Path root) {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    java.util.Map.of(
                        "api", new ProjectConfig("/api", "origin/main", "dev", List.of()),
                        "web",
                            new ProjectConfig(
                                new ProjectConfig.Repository("/web", List.of(), null, null),
                                new ProjectConfig.Branches("main", "dev", null),
                                new ProjectConfig.CodeHost(
                                    "none", null, null, null, null, null, null),
                                new ProjectConfig.Verify(List.of(), null)),
                        "docs", new ProjectConfig("/docs", "origin/main", "dev", List.of()))));
    MergedRequests requests =
        new MergedRequests(
            new StateService(
                new JsonMapper(),
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString()))),
            config,
            inspection,
            new WorktreeChanges(config, inspection),
            departure);

    List<String> owing =
        requests.owingRequest(
            List.of(
                TaskRepo.of("api", "/wt-api"),
                TaskRepo.of("web", "/wt-web"),
                new TaskRepo("docs", "/wt-docs", null, "http://mr/docs")));

    assertThat(owing).containsExactly("api");
  }

  @Test
  void aRepositoryWhoseRequestTheHostClosedLeavesTheTaskWhereTheDepartureTakesIt(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskRepo web = new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web");
    when(departure.leave("ABC-42", web, "its request was closed on the host")).thenReturn(true);

    boolean left =
        new MergedRequests(
                state,
                config,
                inspection,
                new WorktreeChanges(config, inspection),
                departure)
            .hostSaidClosed("ABC-42", web);

    assertThat(left).isTrue();
  }

  @Test
  void aRepositoryTheDepartureKeepsStaysWhoseRequestTheHostClosed(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskRepo primary = new TaskRepo("api", "/api-wt", "git@host:g/api.git", "http://mr/api");
    when(departure.leave("ABC-42", primary, "its request was closed on the host"))
        .thenReturn(false);

    boolean left =
        new MergedRequests(
                state,
                config,
                inspection,
                new WorktreeChanges(config, inspection),
                departure)
            .hostSaidClosed("ABC-42", primary);

    assertThat(left).isFalse();
  }
}

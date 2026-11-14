package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class MergedRequestsTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

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
                    .withStateFile(root.resolve("state.json").toString())),
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
  void requestStaysMergedWhereTheHostSaidSoAndGitSeesNoMergeCommit() {
    state.putTask("ABC-42", reviewed.withMergedByHost("proj"));

    boolean none = requests.refresh("ABC-42");

    assertThat(none).isTrue();
  }

  @Test
  void requestStaysMergedWhereTheHostSaidMergedAndNothingElseIsKnown() {
    state.putTask("ABC-42", reviewed);

    requests.hostSaidMerged("ABC-42", "proj");

    assertThat(state.task("ABC-42").orElseThrow().requestsMerged()).isTrue();
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
            ConfigService.ConfigFile.defaults()
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
    List<String> owing =
        requests.owingRequest(
            List.of(
                TaskRepo.of("api", "/wt-api"),
                TaskRepo.of("web", "/wt-web"),
                new TaskRepo("docs", "/wt-docs", null, "http://mr/docs")));

    assertThat(owing).containsExactly("api");
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

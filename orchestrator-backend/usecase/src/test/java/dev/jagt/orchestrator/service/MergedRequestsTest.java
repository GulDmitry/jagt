package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  void takesARepositoryWhoseRequestTheHostClosedOutOfATaskHoldingOthers(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskRepo web = new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web");
    state.putTask(
        "ABC-42",
        TaskState.builder(
                List.of(new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"), web),
                TaskStatus.REVIEWED)
            .build());

    boolean left =
        new MergedRequests(
                state, config, inspection, new WorktreeChanges(config, inspection), departure)
            .hostSaidClosed("ABC-42", web);

    assertThat(left).isTrue();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api");
    verify(departure).settle(eq("ABC-42"), eq(web), any());
  }

  @Test
  void aRepositoryWhoseRequestTheHostClosedTakesItsTrustedGitEntriesWithIt(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskRepo web = new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web");
    state.putTask(
        "ABC-42",
        TaskState.builder(
                List.of(new TaskRepo("api", "/api-wt", "git@host:g/api.git", "http://mr/api"), web),
                TaskStatus.REVIEWED)
            .gitTrust(
                GitTrust.of(
                    Map.of(
                        "core.bare in /api/.git/config",
                        "a1",
                        "core.bare in /web/.git/config",
                        "b2")))
            .build());
    when(departure.trustedOnlyBy(eq("ABC-42"), any(), eq(web)))
        .thenReturn(Set.of("core.bare in /web/.git/config"));

    new MergedRequests(
            state, config, inspection, new WorktreeChanges(config, inspection), departure)
        .hostSaidClosed("ABC-42", web);

    assertThat(state.task("ABC-42").orElseThrow().gitTrust().recorded())
        .containsOnlyKeys("core.bare in /api/.git/config");
  }

  @Test
  void keepsTheOnlyRepositoryOfATaskWhoseRequestTheHostClosed(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskRepo only = new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api");
    state.putTask("ABC-42", TaskState.builder(List.of(only), TaskStatus.REVIEWED).build());

    boolean left =
        new MergedRequests(
                state, config, inspection, new WorktreeChanges(config, inspection), departure)
            .hostSaidClosed("ABC-42", only);

    assertThat(left).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api");
    verifyNoInteractions(departure);
  }

  @Test
  void keepsThePrimaryRepositoryOfATaskHoldingOthersWhoseRequestTheHostClosed(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    TaskRepo primary = new TaskRepo("api", "/api-wt", "git@host:g/api.git", "http://mr/api");
    state.putTask(
        "ABC-42",
        TaskState.builder(
                List.of(
                    primary,
                    new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                TaskStatus.REVIEWED)
            .build());

    boolean left =
        new MergedRequests(
                state, config, inspection, new WorktreeChanges(config, inspection), departure)
            .hostSaidClosed("ABC-42", primary);

    assertThat(left).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    verifyNoInteractions(departure);
  }
}

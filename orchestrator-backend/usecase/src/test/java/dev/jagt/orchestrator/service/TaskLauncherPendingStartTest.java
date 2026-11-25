package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.service.launch.PendingStarts;
import dev.jagt.orchestrator.service.launch.TaskLaunches;
import dev.jagt.orchestrator.task.launch.LaunchRequest;
import dev.jagt.orchestrator.task.launch.Launched;
import dev.jagt.orchestrator.task.launch.PendingStart;
import dev.jagt.orchestrator.task.launch.PendingStart.Placement;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class TaskLauncherPendingStartTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final TaskLaunches launches = mock(TaskLaunches.class);

  @Test
  void ticketStartIsOnDiskWithItsInstructionsWhileTheTicketIsRead(@TempDir Path root) {
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    List<PendingStart> seenAtTheRead = new ArrayList<>();
    when(launches.ticket(any(), any()))
        .thenAnswer(read -> {
          seenAtTheRead.addAll(
              new PendingStarts(paths, new JsonMapper(), new FakeClock()).interrupted());
          throw new IllegalStateException("the server stopped");
        });
    var launcher =
        new TaskLauncher(
            configService,
            launches,
            mock(TaskResume.class),
            new PendingStarts(paths, new JsonMapper(), new FakeClock()));

    assertThatThrownBy(
            () -> launcher.launch(LaunchRequest.of("ABC-42").withNotes("tighten the parser")))
        .isInstanceOf(IllegalStateException.class);

    assertThat(seenAtTheRead).hasSize(1);
    assertThat(seenAtTheRead.getFirst().key()).isEqualTo("ABC-42");
    assertThat(seenAtTheRead.getFirst().request().notes()).isEqualTo("tighten the parser");
  }

  @Test
  void writtenStartIsOnDiskWithItsWordsWhileItsBranchIsNamed(@TempDir Path root) {
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    List<PendingStart> seenAtTheNaming = new ArrayList<>();
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of("api", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
    when(launches.written(any(), any()))
        .thenAnswer(naming -> {
          seenAtTheNaming.addAll(
              new PendingStarts(paths, new JsonMapper(), new FakeClock()).interrupted());
          return Launched.created("tighten-the-parser", "started");
        });
    var launcher =
        new TaskLauncher(
            configService,
            launches,
            mock(TaskResume.class),
            new PendingStarts(paths, new JsonMapper(), new FakeClock()));

    launcher.launchLine("api tighten the parser");

    assertThat(seenAtTheNaming).hasSize(1);
    assertThat(seenAtTheNaming.getFirst().request().notes()).isEqualTo("tighten the parser");
  }

  @Test
  void finishedStartLeavesNothingBehind(@TempDir Path root) {
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    when(launches.ticket(any(), any())).thenReturn(Launched.created("ABC-42", "started"));
    var launcher =
        new TaskLauncher(
            configService,
            launches,
            mock(TaskResume.class),
            new PendingStarts(paths, new JsonMapper(), new FakeClock()));

    launcher.launch(LaunchRequest.of("ABC-42"));

    assertThat(new PendingStarts(paths, new JsonMapper(), new FakeClock()).interrupted()).isEmpty();
  }

  @Test
  void refusedStartLeavesNothingBehind(@TempDir Path root) {
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    var launcher =
        new TaskLauncher(
            configService,
            launches,
            mock(TaskResume.class),
            new PendingStarts(paths, new JsonMapper(), new FakeClock()));

    launcher.launch(LaunchRequest.defaults());

    assertThat(new PendingStarts(paths, new JsonMapper(), new FakeClock()).interrupted()).isEmpty();
  }

  @Test
  void resumingStartRunsItsSavedRequestAndClearsItsRecord(@TempDir Path root) {
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    LaunchRequest saved = LaunchRequest.of("ABC-42").withNotes("tighten the parser");
    new PendingStarts(paths, new JsonMapper(), new FakeClock())
        .begin("ABC-42", saved, Placement.PLACED);
    when(launches.ticket(saved, null)).thenReturn(Launched.created("ABC-42", "started"));
    var launcher =
        new TaskLauncher(
            configService,
            launches,
            mock(TaskResume.class),
            new PendingStarts(paths, new JsonMapper(), new FakeClock()));

    Launched resumed = launcher.resumeStart("ABC-42");

    assertThat(resumed.created()).isTrue();
    assertThat(new PendingStarts(paths, new JsonMapper(), new FakeClock()).interrupted()).isEmpty();
  }

  @Test
  void resumingStartNobodyKeptIsRefused(@TempDir Path root) {
    var launcher =
        new TaskLauncher(
            configService,
            launches,
            mock(TaskResume.class),
            new PendingStarts(
                orchestratorPaths().withRoot(root).build(), new JsonMapper(), new FakeClock()));

    assertThatThrownBy(() -> launcher.resumeStart("ABC-42"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

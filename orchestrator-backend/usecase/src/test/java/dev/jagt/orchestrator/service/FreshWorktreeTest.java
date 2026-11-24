package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.worktree.SubAgentBriefing;
import dev.jagt.orchestrator.task.launch.NewTask;
import dev.jagt.orchestrator.task.repo.NewRepo;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class FreshWorktreeTest {

  private final Processes processes = mock(Processes.class);
  private final FreshWorktree fresh = new FreshWorktree(mock(SubAgentBriefing.class), processes);
  private final NewTask request = NewTask.builder("ABC-42", "api").build();
  private final NewRepo seeded =
      new NewRepo(
          "api",
          TestProjectConfigBuilder.projectConfig()
              .withSetup(
                  new ProjectConfig.Setup(
                      List.of("seed", "{branch}", "{worktree}"), null, List.of()))
              .build(),
          Path.of("/repo"),
          Path.of("/wt/ABC-42"),
          Path.of("/repo/.git"),
          "origin/main",
          "u",
          true);

  @Test
  void runsSetupCommandInTheNewWorktreeWithBranchAndWorktreeFilled() {
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "", ""));

    fresh.setUp(request, seeded);

    verify(processes)
        .run(
            Path.of("/wt/ABC-42"), Duration.ofMinutes(20), List.of("seed", "ABC-42", "/wt/ABC-42"));
  }

  @Test
  void runsSetupUnderTheProjectsSetupMinutes() {
    NewRepo limited =
        new NewRepo(
            "api",
            TestProjectConfigBuilder.projectConfig()
                .withSetup(new ProjectConfig.Setup(List.of("seed"), 3, List.of()))
                .build(),
            Path.of("/repo"),
            Path.of("/wt/ABC-42"),
            Path.of("/repo/.git"),
            "origin/main",
            "u",
            true);
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "", ""));

    fresh.setUp(request, limited);

    verify(processes).run(Path.of("/wt/ABC-42"), Duration.ofMinutes(3), List.of("seed"));
  }

  @Test
  void runsNothingWhereTheProjectNamesNoSetupCommand() {
    NewRepo plain =
        new NewRepo(
            "api",
            TestProjectConfigBuilder.projectConfig().build(),
            Path.of("/repo"),
            Path.of("/wt/ABC-42"),
            Path.of("/repo/.git"),
            "origin/main",
            "u",
            true);

    fresh.setUp(request, plain);

    verifyNoInteractions(processes);
  }

  @Test
  void failsNamingProjectExitAndOutputTailWhereSetupExitsNonZero() {
    when(processes.run(any(), any(), anyList()))
        .thenReturn(new Processes.Result(3, "", "cannot link node_modules"));

    assertThatThrownBy(() -> fresh.setUp(request, seeded))
        .isInstanceOf(SetupFailed.class)
        .hasMessageContaining("[api]")
        .hasMessageContaining("exited 3")
        .hasMessageContaining("cannot link node_modules");
  }

  @Test
  void failsAsRefusalTheBoardAnswersWhereSetupExitsNonZero() {
    when(processes.run(any(), any(), anyList()))
        .thenReturn(new Processes.Result(3, "", "cannot link node_modules"));

    assertThatThrownBy(() -> fresh.setUp(request, seeded))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsNamingProjectWhereSetupPassesItsLimit() {
    when(processes.run(any(), any(), anyList()))
        .thenThrow(new IllegalStateException("Command timed out after PT20M: seed"));

    assertThatThrownBy(() -> fresh.setUp(request, seeded))
        .isInstanceOf(SetupFailed.class)
        .hasMessageContaining("[api]")
        .hasMessageContaining("timed out");
  }

  @Test
  void keepsTheLastLinesOfLongFailure() {
    String output = "first line\n" + "x\n".repeat(60) + "last line";
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, output, ""));

    assertThatThrownBy(() -> fresh.setUp(request, seeded))
        .satisfies(
            e -> assertThat(e.getMessage()).contains("last line").doesNotContain("first line"));
  }
}

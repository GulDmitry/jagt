package dev.jagt.orchestrator.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.capability.deploy.GitCaseFacts;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class HandOffCapabilityTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @Test
  void saysTheSessionTheCaseOfEachRepositoryItStandsIn(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer, event -> {});
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build());
    ConfigService config = mock(ConfigService.class);
    when(config.project("proj"))
        .thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
    GitCaseFacts facts = mock(GitCaseFacts.class);
    when(facts.cases(Path.of("/repo"), "ABC-1", "proj", "dev"))
        .thenReturn(List.of("proj: the deploy worktree waits; run `git merge origin/dev` in it"));
    AgentSessions sessions = mock(AgentSessions.class);
    HandOffCapability capability =
        new HandOffCapability(state, new DeployTargets(config), facts, sessions);

    Outcome outcome = capability.run("a1");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
    verify(sessions)
        .say(
            "ABC-1",
            "ABC-1 stands in a git case, work it: proj: the deploy worktree"
                + " waits; run `git merge origin/dev` in it");
  }

  @Test
  void refusesAndSaysNothingWhereNoRepositoryStandsInGitCase(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer, event -> {});
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build());
    ConfigService config = mock(ConfigService.class);
    when(config.project("proj"))
        .thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
    AgentSessions sessions = mock(AgentSessions.class);
    HandOffCapability capability =
        new HandOffCapability(state, new DeployTargets(config), mock(GitCaseFacts.class), sessions);

    assertThatThrownBy(() -> capability.run("a1"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("no repository of it stands in a git case");
    verify(sessions, never()).say(anyString(), anyString());
  }
}

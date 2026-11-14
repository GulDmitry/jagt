package dev.jagt.orchestrator.capability.session;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.capability.deploy.GitCaseFacts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.deploy.DeployTargets;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandOffCapabilityTest {

  @TempDir Path root;

  ConfigService config = mock(ConfigService.class);
  AgentSessions sessions = mock(AgentSessions.class);
  StateService state;

  @BeforeEach
  void deployedTaskOfProjectProj() {
    state = stateService().withRoot(root).build();
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build());
    when(config.project("proj"))
        .thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
  }

  @Test
  void saysTheSessionTheCaseOfEachRepositoryItStandsIn() {
    GitCaseFacts facts = mock(GitCaseFacts.class);
    when(facts.cases(Path.of("/repo"), "ABC-1", "proj", "dev"))
        .thenReturn(List.of("proj: the deploy worktree waits; run `git merge origin/dev` in it"));
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
  void refusesAndSaysNothingWhereNoRepositoryStandsInGitCase() {
    HandOffCapability capability =
        new HandOffCapability(state, new DeployTargets(config), mock(GitCaseFacts.class), sessions);

    assertThatThrownBy(() -> capability.run("a1"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("no repository of it stands in a git case");
    verify(sessions, never()).say(anyString(), anyString());
  }
}

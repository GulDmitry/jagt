package dev.jagt.orchestrator.capability;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CodeHost;
import dev.jagt.orchestrator.service.RepoDeparture;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoRemovalTest {

  @TempDir Path root;

  private final CodeHost host = mock(CodeHost.class);
  private final RepoDeparture departure = mock(RepoDeparture.class);
  private StateService state;

  @BeforeEach
  void stateOverTheTempRoot() {
    state = stateService().withRoot(root).build();
  }

  @Test
  void closesTheRequestOnItsHostThenTakesTheRepositoryOutOfTheTask() {
    TaskRepo web = new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web");
    state.putTask(
        "ABC-42",
        TaskState.builder(
                List.of(new TaskRepo("api", "/api-wt", "git@host:g/api.git", "http://mr/api"), web),
                TaskStatus.REVIEWED)
            .build());
    when(host.closeRequest("web", "git@host:g/web.git", "http://mr/web"))
        .thenReturn(new Answer<>(Optional.of("closed"), TokenUsage.NONE));

    String said = new RepoRemoval(state, host, departure).remove("ABC-42", "web");

    assertThat(said).isEqualTo("remove-repo ABC-42: web left the task, its request closed");
    verify(departure).leave("ABC-42", web, "you removed it");
  }

  @Test
  void takesRepositoryWithNoRequestOutWithoutAskingHost() {
    TaskRepo web = TaskRepo.of("web", "/web-wt");
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.IN_PROGRESS)
            .build());

    String said = new RepoRemoval(state, host, departure).remove("ABC-42", "web");

    assertThat(said).isEqualTo("remove-repo ABC-42: web left the task");
    verify(departure).leave("ABC-42", web, "you removed it");
    verifyNoInteractions(host);
  }

  @Test
  void leavesTheRepositoryOnTheTaskAndNamesTheHostsCauseWhenTheCloseFails() {
    state.putTask(
        "ABC-42",
        TaskState.builder(
                List.of(
                    TaskRepo.of("api", "/api-wt"),
                    new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                TaskStatus.REVIEWED)
            .build());
    when(host.closeRequest("web", "git@host:g/web.git", "http://mr/web"))
        .thenReturn(Answer.failed(TokenUsage.NONE, "gh exited 1: no permission"));

    assertThatThrownBy(() -> new RepoRemoval(state, host, departure).remove("ABC-42", "web"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("gh exited 1: no permission");

    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    verifyNoInteractions(departure);
  }

  @Test
  void closesNothingForRequestTheHostMerged() {
    state.putTask(
        "ABC-42",
        TaskState.builder(
                List.of(
                    TaskRepo.of("api", "/api-wt"),
                    new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")
                        .withMergedByHost()),
                TaskStatus.REVIEWED)
            .build());

    new RepoRemoval(state, host, departure).remove("ABC-42", "web");

    verify(departure).leave(eq("ABC-42"), any(), any());
    verifyNoInteractions(host);
  }

  @Test
  void refusesProjectTheTaskDoesNotWorkIn() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.IN_PROGRESS)
            .build();

    assertThatThrownBy(() ->
            new RepoRemoval(mock(StateService.class), host, departure).vet("ABC-42", task, "docs"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("does not work in docs");
  }

  @Test
  void refusesThePrimaryRepositoryBecauseTheSessionStandsInIt() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.IN_PROGRESS)
            .build();

    assertThatThrownBy(() ->
            new RepoRemoval(mock(StateService.class), host, departure).vet("ABC-42", task, "api"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("primary repository");
  }

  @Test
  void refusesRepositoryAlreadyDeployedAndNamesTheRevert() {
    Deployment deployed =
        Deployment.defaults()
            .withTargets(List.of(Deployment.Target.of("web", "dev").withMerge("abc123")));
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.DEPLOYED)
            .deployment(deployed)
            .build();

    assertThatThrownBy(() ->
            new RepoRemoval(mock(StateService.class), host, departure).vet("ABC-42", task, "web"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("revert first");
  }

  @Test
  void acceptsRepositoryTheTaskHoldsBesideItsPrimary() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.IN_PROGRESS)
            .build();

    assertThatCode(() ->
            new RepoRemoval(mock(StateService.class), host, departure).vet("ABC-42", task, "web"))
        .doesNotThrowAnyException();
  }
}

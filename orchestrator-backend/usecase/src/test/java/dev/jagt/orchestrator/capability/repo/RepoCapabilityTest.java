package dev.jagt.orchestrator.capability.repo;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FactSources;
import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.capability.Capabilities;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

class RepoCapabilityTest {

  private final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  private final RepoAttachment attachment = mock(RepoAttachment.class);

  @Test
  void handsTheSessionBackToWorkOnceTheRepositoryIsAddedAndTheSessionTold() {
    when(attachment.attach("ABC-7"))
        .thenReturn("add-repo ABC-7: registry is at /wt; the session was told");

    Outcome outcome = new RepoCapability(attachment).run("ABC-7");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
  }

  @Test
  void refusesWithTheCauseOnTheCardWhenTheWorktreeCouldNotBeCut() {
    when(attachment.attach("ABC-7")).thenThrow(new IllegalStateException("git: fetch failed"));

    Outcome outcome = new RepoCapability(attachment).run("ABC-7");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
    assertThat(outcome.stamp()).isEqualTo("add-repo failed: git: fetch failed");
    assertThat(outcome.stampMeans()).isEqualTo(AgentReport.QUESTION);
  }

  @Nested
  class OverTheEngine {

    @TempDir Path root;

    StateService state;
    FlowEngine engine;

    @BeforeEach
    void engineOverStateInTheTempRoot() {
      state = stateService().withRoot(root).build();
      engine =
          new FlowEngine(
              state,
              new Capabilities(List.of(new RepoCapability(attachment)), List.of()),
              new FactSources(
                  mock(AgentPresence.class),
                  ignored -> false,
                  id -> Optional.empty(),
                  id -> false,
                  () -> ActionOrigin.BOARD,
                  id -> Optional.empty()),
              new FakeClock(),
              rules);
    }

    @Test
    void leavesTheRequestStandingAndTheCardAskingWhenThePressFails() {
      state.putTask(
          "ABC-7",
          TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
              .alias("a1")
              .message("needs the registry")
              .report(AgentReport.QUESTION)
              .repoRequest("registry")
              .build());
      when(attachment.attach("ABC-7")).thenThrow(new IllegalStateException("git: fetch failed"));

      assertThatThrownBy(() -> engine.run("ABC-7", TaskAction.ADD_REPO))
          .isInstanceOf(IllegalStateException.class);

      TaskState task = state.task("ABC-7").orElseThrow();
      assertThat(task.work().repoRequest()).isEqualTo("registry");
      assertThat(task.standing().status()).isEqualTo(TaskStatus.IN_PROGRESS);
      assertThat(task.standing().message()).isEqualTo("add-repo failed: git: fetch failed");
      assertThat(task.standing().report()).isEqualTo(AgentReport.QUESTION);
    }

    @Test
    void putsTheTaskBackToWorkWhenThePressSucceeds() {
      state.putTask(
          "ABC-7",
          TaskState.builder("api", "/wt", TaskStatus.CI_FAILED)
              .alias("a1")
              .message("needs the registry")
              .report(AgentReport.QUESTION)
              .repoRequest("registry")
              .build());
      when(attachment.attach("ABC-7"))
          .thenReturn("add-repo ABC-7: registry is at /wt2; the session was told");

      engine.run("ABC-7", TaskAction.ADD_REPO);

      assertThat(state.task("ABC-7").orElseThrow().standing().status())
          .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void addsProjectHumanNamesToTaskThatAskedForNoneAndPutsItBackToWork() {
      state.putTask(
          "ABC-7", TaskState.builder("api", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
      when(attachment.attach("ABC-7"))
          .thenReturn("add-repo ABC-7: registry is at /wt2; the session was told");

      engine.run("ABC-7", TaskAction.ADD_REPO, "registry");

      assertThat(state.task("ABC-7").orElseThrow().standing().status())
          .isEqualTo(TaskStatus.IN_PROGRESS);
      verify(attachment).vet("registry");
    }

    @Test
    void changesNothingWhenTheNamedProjectIsNotConfigured() {
      state.putTask(
          "ABC-7", TaskState.builder("api", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
      doThrow(Refusal.byState("Unknown project 'gamma'")).when(attachment).vet("gamma");

      assertThatThrownBy(() -> engine.run("ABC-7", TaskAction.ADD_REPO, "gamma"))
          .isInstanceOf(Refusal.class);

      TaskState task = state.task("ABC-7").orElseThrow();
      assertThat(task.work().repoRequest()).isNull();
      assertThat(task.standing().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
      verify(attachment, never()).attach("ABC-7");
    }
  }

  @Test
  void refusesNamedProjectTheTaskAlreadyWorksIn() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt"), TaskRepo.of("registry", "/wt2")),
                TaskStatus.IN_PROGRESS)
            .build();

    assertThatThrownBy(() -> new RepoCapability(attachment).vet("ABC-7", task, "registry"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("already works in registry");
  }

  @Test
  void recordsTheProjectHumanNamesAsTheTasksRequestBeforeItAttaches() {
    when(attachment.attach("ABC-7"))
        .thenReturn("add-repo ABC-7: registry is at /wt2; the session was told");

    Outcome outcome = new RepoCapability(attachment).run("ABC-7", "registry");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
    InOrder order = inOrder(attachment);
    order.verify(attachment).ask("ABC-7", "registry");
    order.verify(attachment).attach("ABC-7");
  }
}

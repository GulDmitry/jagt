package dev.jagt.orchestrator.job.deploy;

import static dev.jagt.orchestrator.job.TestDeployCheckTaskBuilder.deployCheckTask;
import static dev.jagt.orchestrator.job.TestStepRelayBuilder.stepRelay;
import static dev.jagt.orchestrator.job.deploy.TestDeployCheckJobBuilder.deployCheckJob;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.session.Standing;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeployCheckJobTest {

  private static final DeployPipelines.Reading GREEN =
      new DeployPipelines.Reading(DeployPipelines.Kind.GREEN, "", "", "", List.of());

  private final ConfigService config = mock(ConfigService.class);
  private final StepRelay relay = mock(StepRelay.class);
  private final DeployPipelines pipelines = mock(DeployPipelines.class);

  @TempDir Path root;

  private StateService state;
  private DeployCheckJob job;

  @BeforeEach
  void givenProjectConfiguredOverAnEmptyState() {
    state = stateService().withRoot(root).build();
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of("proj", new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
    job = deployCheckJob().withRoot(root).build(state, config, relay, pipelines);
  }

  @Test
  void asksNothingWhileTheFixOfFailedCheckIsUnderway() {
    state.putTask("ABC-42", deployCheckTask().withStatus(TaskStatus.IN_PROGRESS).build());

    job.runOn(state.tasks());

    verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
    verify(pipelines, never()).read(anyString(), any());
    assertThat(state.task("ABC-42").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void asksNothingOfDeployedTaskWhoseDeployLeftNoCommit() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    job.runOn(state.tasks());

    verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void asksOncePerDeployThoughAnotherRelayFollowedAndJagtRestarted() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Path worktree = Files.createDirectories(root.resolve("ABC-42"));
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", worktree.toString(), TaskStatus.DEPLOYED)
            .build()
            .withDeployment(Objects.requireNonNull(deployCheckTask().build().work().deployment())));
    when(pipelines.read(eq("ABC-42"), any())).thenReturn(GREEN);
    AgentSessions sessions = mock(AgentSessions.class);
    StepRelay firstRelay = stepRelay().withRoot(root).build(state, sessions, new FakeClock());
    deployCheckJob()
        .withRoot(root)
        .build(state, config, firstRelay, pipelines)
        .runOn(state.tasks());
    firstRelay.relay(
        "ABC-42",
        Step.ANSWERED,
        "The Master answered your question, standing in for the human:\nyes",
        firstRelay.standing("ABC-42"));
    StateService restarted = stateService().withRoot(root).build();
    StepRelay restartedRelay =
        stepRelay().withRoot(root).build(restarted, sessions, new FakeClock());

    deployCheckJob()
        .withRoot(root)
        .build(restarted, config, restartedRelay, pipelines)
        .runOn(restarted.tasks());

    verify(sessions, times(1)).writeTaskContext(eq("ABC-42"), contains("ABC-42 is deployed"));
  }

  @Test
  void readsNoPipelineAgainForDeployWhoseCheckWasAlreadyAsked() {
    TaskState deployed = deployCheckTask().build();
    state.putTask(
        "ABC-42",
        deployed.withDeployment(
            Objects.requireNonNull(deployed.work().deployment())
                .withAskedDigest(Turn.digestOf("ABC-42 is deployed:\n- proj: 01234567 on dev"))));

    job.runOn(state.tasks());

    verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
    verify(pipelines, never()).read(anyString(), any());
  }

  @Nested
  class WhereTheDeployLanded {

    @BeforeEach
    void givenDeployedTask() {
      state.putTask("ABC-42", deployCheckTask().build());
    }

    @Test
    void handsBackPipelineRedOnTheCodeThatTheHostListedAfterTheDeployStoodWithNone() {
      when(relay.standing("ABC-42")).thenReturn(Standing.ABSENT);
      when(relay.relay(eq("ABC-42"), eq(Step.DEPLOY), anyString(), any(Standing.class)))
          .thenReturn(true);
      when(pipelines.read(eq("ABC-42"), any()))
          .thenReturn(
              new DeployPipelines.Reading(DeployPipelines.Kind.GREEN, "", "", "", List.of("proj")),
              new DeployPipelines.Reading(
                  DeployPipelines.Kind.RED,
                  "red pipeline on the deploy commit: proj 01234567",
                  "proj",
                  "https://git.example.invalid/p/7",
                  List.of()));
      job.runOn(state.tasks());

      job.runOn(state.tasks());

      assertThat(state.task("ABC-42").orElseThrow().standing().status())
          .isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    }

    @Test
    void stopsTheTaskOnTheThirdReadRunningThatCannotListItsDeployPipeline() {
      when(pipelines.read(eq("ABC-42"), any()))
          .thenReturn(
              new DeployPipelines.Reading(
                  DeployPipelines.Kind.UNREAD,
                  "proj 01234567: pipeline unread, glab not logged in",
                  "proj",
                  "",
                  List.of()));
      job.runOn(state.tasks());
      job.runOn(state.tasks());

      job.runOn(state.tasks());

      assertThat(Objects.requireNonNull(state.task("ABC-42").orElseThrow().work().stop()).trigger())
          .isEqualTo(TaskStop.Trigger.DEPLOY_UNREAD);
    }

    @Test
    void asksNothingAndSaysItWaitsOnItsPipelineWhileTheDeployPipelineIsRunning() {
      when(pipelines.read(eq("ABC-42"), any()))
          .thenReturn(
              new DeployPipelines.Reading(
                  DeployPipelines.Kind.WAITING,
                  "proj 01234567: pipeline running",
                  "",
                  "",
                  List.of()));

      job.runOn(state.tasks());

      verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
      assertThat(state.task("ABC-42").orElseThrow().standing().message())
          .isEqualTo("deployed to dev — waiting on its pipeline: proj 01234567: pipeline running");
    }

    @Test
    void handsDeployBackToItsSessionWhenItsPipelineIsRedOnTheCodeAndAsksNothing() {
      when(pipelines.read(eq("ABC-42"), any()))
          .thenReturn(
              new DeployPipelines.Reading(
                  DeployPipelines.Kind.RED,
                  "red pipeline on the deploy commit: proj 01234567",
                  "proj",
                  "https://git.example.invalid/p/7",
                  List.of()));

      job.runOn(state.tasks());

      TaskState handed = state.task("ABC-42").orElseThrow();
      assertThat(handed.standing().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
      assertThat(handed.standing().message())
          .isEqualTo(
              "red pipeline on the deploy commit: proj 01234567 https://git.example.invalid/p/7");
      verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
    }

    @Nested
    class AndItsPipelineIsGreen {

      @BeforeEach
      void givenGreenPipelineAndNoSessionStanding() {
        when(pipelines.read(eq("ABC-42"), any())).thenReturn(GREEN);
        when(relay.standing("ABC-42")).thenReturn(Standing.ABSENT);
      }

      @Test
      void asksDeployedTasksSessionToCheckItWhereItLandedOnceItsPipelineIsGreen() {
        job.runOn(state.tasks());

        verify(relay)
            .relay(
                eq("ABC-42"),
                eq(Step.DEPLOY),
                contains("- proj: 01234567 on dev"),
                any(Standing.class));
      }

      @Test
      void namesAnEightCharacterCommitWhole() {
        state.putTask("ABC-42", deployCheckTask().withMerge("01234567").build());

        job.runOn(state.tasks());

        verify(relay)
            .relay(
                eq("ABC-42"),
                eq(Step.DEPLOY),
                contains("- proj: 01234567 on dev"),
                any(Standing.class));
      }

      @Test
      void namesTheDeployBranchOfNoProjectTheConfigDoesNotHold() {
        when(config.load()).thenReturn(ConfigFile.defaults());

        job.runOn(state.tasks());

        verify(relay)
            .relay(
                eq("ABC-42"),
                eq(Step.DEPLOY),
                contains("- proj: 01234567 on its deploy branch"),
                any(Standing.class));
      }

      @Test
      void recordsTheDeployAskedOnceItsSessionWasHanded() {
        when(relay.relay(eq("ABC-42"), eq(Step.DEPLOY), anyString(), any(Standing.class)))
            .thenReturn(true);

        job.runOn(state.tasks());

        assertThat(
                Objects.requireNonNull(state.task("ABC-42").orElseThrow().work().deployment())
                    .askedDigest())
            .isEqualTo(Turn.digestOf("ABC-42 is deployed:\n- proj: 01234567 on dev"));
      }

      @Test
      void recordsNoDeployAskedWhereTheSessionWasNotHanded() {
        job.runOn(state.tasks());

        assertThat(
                Objects.requireNonNull(state.task("ABC-42").orElseThrow().work().deployment())
                    .askedDigest())
            .isNull();
      }
    }
  }

  @Nested
  class WhereAnUnreadReadWasCounted {

    @BeforeEach
    void givenDeployedTaskWhoseLastReadWasUnread() {
      state.putTask("ABC-42", deployCheckTask().withUnread(true).build());
    }

    @Test
    void stopsNoTaskAndKeepsItsUnreadCountWhileJagtCannotReachTheHost() {
      TaskState once = deployCheckTask().withUnread(true).build();
      state.putTask(
          "ABC-42",
          once.withDeployment(
              Objects.requireNonNull(once.work().deployment())
                  .withTarget("proj", target -> target.withUnread(true))));
      when(pipelines.read(eq("ABC-42"), any()))
          .thenReturn(
              new DeployPipelines.Reading(
                  DeployPipelines.Kind.UNREACHED,
                  "proj 01234567: pipeline unreached, glab not logged in",
                  "proj",
                  "",
                  List.of()));
      job.runOn(state.tasks());
      job.runOn(state.tasks());

      job.runOn(state.tasks());

      TaskState task = state.task("ABC-42").orElseThrow();
      assertThat(task.work().stopped()).isFalse();
      assertThat(
              Objects.requireNonNull(task.work().deployment()).targets().getFirst().unreadReads())
          .isEqualTo(2);
    }

    @Test
    void forgetsTheUnreadReadsOnceReadListsThePipeline() {
      when(pipelines.read(eq("ABC-42"), any()))
          .thenReturn(
              new DeployPipelines.Reading(
                  DeployPipelines.Kind.WAITING,
                  "proj 01234567: pipeline running",
                  "",
                  "",
                  List.of()));

      job.runOn(state.tasks());

      assertThat(
              Objects.requireNonNull(state.task("ABC-42").orElseThrow().work().deployment())
                  .targets()
                  .getFirst()
                  .unreadReads())
          .isZero();
    }

    @Nested
    class AndTheDeployIsStoppedForIt {

      @BeforeEach
      void givenUnreadDeployStop() {
        TaskState unread = state.task("ABC-42").orElseThrow();
        state.putTask(
            "ABC-42",
            unread.withStop(
                new TaskStop(
                    TaskStop.Trigger.DEPLOY_UNREAD, 1_000, "3 reads running could not list it")));
      }

      @Test
      void endsTheUnreadDeployStopOnTheFirstReadThatListsThePipeline() {
        when(pipelines.read(eq("ABC-42"), any()))
            .thenReturn(
                new DeployPipelines.Reading(
                    DeployPipelines.Kind.WAITING,
                    "proj 01234567: pipeline running",
                    "",
                    "",
                    List.of()));

        job.runOn(state.tasks());

        assertThat(state.task("ABC-42").orElseThrow().work().stopped()).isFalse();
      }

      @ParameterizedTest
      @CsvSource(
          delimiter = '|',
          value = {
            "UNREAD|proj 01234567: pipeline unread, glab not logged in",
            "UNREACHED|proj 01234567: pipeline unreached, glab not logged in"
          })
      void keepsTheUnreadDeployStopWhileTheReadCannotListThePipelineOrReachTheHost(
          DeployPipelines.Kind kind, String summary) {
        when(pipelines.read(eq("ABC-42"), any()))
            .thenReturn(new DeployPipelines.Reading(kind, summary, "proj", "", List.of()));

        job.runOn(state.tasks());

        assertThat(state.task("ABC-42").orElseThrow().work().stopped()).isTrue();
      }
    }
  }
}

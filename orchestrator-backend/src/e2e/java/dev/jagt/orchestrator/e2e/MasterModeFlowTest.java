package dev.jagt.orchestrator.e2e;

import static dev.jagt.orchestrator.e2e.TestJudgementBuilder.judgement;
import static dev.jagt.orchestrator.e2e.TestNewTaskBuilder.newTask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.service.assistant.Assistants;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.StatusChange;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@E2eFlow(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MasterModeFlowTest {

  private static final String TASK = "ABC-7";

  @TempDir static Path workspace;

  @DynamicPropertySource
  static void orchestratorLivesInTheTempWorkspace(DynamicPropertyRegistry registry) {
    FlowHarness.livesIn(registry, workspace);
  }

  @MockitoBean(answers = RETURNS_DEEP_STUBS)
  private Assistants assistants;

  @MockitoBean private McpHealth mcp;
  @MockitoSpyBean private Clock clock;
  private final UserNotifier userNotifier;
  private final TaskProvisioning provisioning;
  private final FlowHarness flow;

  @Autowired
  MasterModeFlowTest(
      UserNotifier userNotifier,
      TaskProvisioning provisioning,
      ApplicationContext context,
      @LocalServerPort int port) {
    this.userNotifier = userNotifier;
    this.provisioning = provisioning;
    this.flow = new FlowHarness(context, workspace, TASK, port);
  }

  @BeforeAll
  static void createTheThrowawayOutsideWorld() throws Exception {
    FlowHarness.createThrowawayWorld(workspace, true);
  }

  @AfterEach
  void leaveNothingBehindForTheNextFlow() {
    flow.leaveNothingBehind();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dev.jagt.orchestrator.e2e.MasterModeCase#plans")
  void readsAndStartsPlanAsTheModeAllows(MasterModeCase master) throws Exception {
    E2eWorkspace.writeConfig(flow.configFile(), flow.repo(), master);
    flow.judges(judgement().withVerdict(master.verdict()).build());
    provisioning.initializeTask(newTask().build());
    Files.writeString(
        flow.worktree().resolve("plan.md"),
        "## Acceptance criteria\n\n- [here] The widget sits left.\n\nMove the widget left.\n");

    flow.agentReports("PLAN_PENDING", "plan written", "");

    assertThat(flow.settled(master).status()).isEqualTo(master.expected());
    assertThat(flow.asked()).isEqualTo(master.read());
    assertThat(flow.taskContext()).contains(master.told());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dev.jagt.orchestrator.e2e.MasterModeCase#questions")
  void answersSessionsQuestionAsTheModeAllows(MasterModeCase master) throws Exception {
    E2eWorkspace.writeConfig(flow.configFile(), flow.repo(), master);
    flow.judges(
        judgement()
            .withFindings(
                List.of(new Finding("", "keep v2 beside v3", "decided", "advice", "", "")))
            .build());
    provisioning.initializeTask(newTask().build());

    flow.agentReports("IN_PROGRESS", "keep v2?", ", \"outcome\": \"question\"");

    assertThat(flow.settled(master).status()).isEqualTo(master.expected());
    assertThat(flow.asked()).isEqualTo(master.read());
    assertThat(flow.taskContext()).contains(master.told());
  }

  @Test
  void leavesPermissionTheSessionAsksForToTheHuman() throws Exception {
    E2eWorkspace.writeConfig(
        flow.configFile(), flow.repo(), MasterModeCase.questions().findFirst().orElseThrow());
    flow.judges(
        judgement()
            .withFindings(List.of(new Finding("", "go ahead", "decided", "advice", "", "")))
            .build());
    provisioning.initializeTask(newTask().build());

    flow.agentReports(
        "IN_PROGRESS", "may I write outside the worktree?", ", \"outcome\": \"permission\"");

    flow.awaitRun("master-answer");
    assertThat(flow.asked()).isFalse();
    assertThat(flow.task().report()).isEqualTo(AgentReport.PERMISSION);
    assertThat(flow.taskContext()).isEqualTo("Fix the widget");
  }

  @Nested
  class WithTheWidgetFixed {

    @BeforeEach
    void theTaskHoldsItsFirstRound() throws Exception {
      E2eWorkspace.writeConfig(
          flow.configFile(), flow.repo(), MasterModeCase.rounds().findFirst().orElseThrow());
      provisioning.initializeTask(newTask().build());
      Files.writeString(flow.worktree().resolve("widget.txt"), "fixed\n");
      Files.writeString(flow.worktree().resolve("task_notes.md"), "widget fixed\n");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.jagt.orchestrator.e2e.MasterModeCase#rounds")
    void readsAndPressesRoundAsTheModeAllows(MasterModeCase master) throws Exception {
      E2eWorkspace.writeConfig(flow.configFile(), flow.repo(), master);
      flow.judges(judgement().withVerdict(master.verdict()).build());

      flow.agentReports("REVIEW_PENDING", "widget fixed", "");

      assertThat(flow.settled(master).status()).isEqualTo(master.expected());
      assertThat(flow.asked()).isEqualTo(master.read());
      assertThat(flow.taskContext()).contains(master.told());
    }

    @Test
    void stampsTheShipItPressesAsTheMastersOwn() throws Exception {
      flow.judges(judgement().build());

      flow.agentReports("REVIEW_PENDING", "widget fixed", "");

      assertThat(flow.awaitTask(task -> task.status() == TaskStatus.SHIPPING).history())
          .filteredOn(change -> change.status() == TaskStatus.SHIPPING)
          .extracting(StatusChange::origin)
          .containsExactly(ActionOrigin.MASTER);
    }

    @Nested
    class ShippedByTheMaster {

      @BeforeEach
      void theMasterShippedTheRoundAndItsRequestIsUp() throws Exception {
        E2eWorkspace.writeConfig(
            flow.configFile(), flow.repo(), MasterModeCase.deploys().findFirst().orElseThrow());
        flow.judges(judgement().build());
        flow.agentReports("REVIEW_PENDING", "widget fixed", "");
        flow.awaitRun("master-review");
        flow.awaitTask(reached -> reached.status() == TaskStatus.SHIPPING);
        flow.pushesTheRoundAndReportsItsRequest();
      }

      @ParameterizedTest(name = "{0}")
      @MethodSource("dev.jagt.orchestrator.e2e.MasterModeCase#deploys")
      void deploysGreenRequestAsTheModeAllows(MasterModeCase master) throws Exception {
        E2eWorkspace.writeConfig(flow.configFile(), flow.repo(), master);

        flow.act("sweep");
        flow.awaitRun("master-deploy");

        assertThat(flow.awaitTask(task -> task.status() == master.expected()).status())
            .isEqualTo(master.expected());
      }

      @Test
      void asksTheSessionToCheckTheDeployOnceItsPipelineIsGreen() throws Exception {
        flow.act("sweep");
        flow.awaitRun("master-deploy");
        flow.awaitTask(task -> task.status() == TaskStatus.DEPLOYED);
        String deployed = E2eWorkspace.git(flow.origin(), "rev-parse", "dev").substring(0, 8);

        flow.awaitRun("deploy-check");

        flow.awaitTold("Make sure it is deployed and works");
        assertThat(flow.taskContext())
            .contains(TASK + " is deployed:\n- proj: " + deployed + " on dev");
      }

      @Nested
      class AtDeployConflict {

        @BeforeEach
        void theSessionWasHandedTheConflict() throws Exception {
          E2eWorkspace.commitOnDeployBranch(flow.repo(), "widget.txt", "someone else's line\n");
          flow.act("sweep");
          flow.awaitRun("master-deploy");
          flow.awaitTask(task -> task.status() == TaskStatus.DEPLOY_CONFLICT);
          flow.awaitRun("deploy-conflict");
          flow.awaitTold("did not deploy");
        }

        @Test
        void handsDeployConflictToTheSessionAndFinishesTheDeployOnceItIsResolved()
            throws Exception {
          Path resolveIn = flow.worktree().resolveSibling(TASK + "-deploy");
          assertThat(
                  Files.readString(flow.worktree().resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE))
                      .lines())
              .anySatisfy(line -> assertThat(line).contains("Also yours", resolveIn.toString()));
          Files.writeString(resolveIn.resolve("widget.txt"), "both sides, resolved\n");
          E2eWorkspace.git(resolveIn, "add", "widget.txt");
          flow.awaitRun("deploy-conflict");

          assertThat(flow.awaitTask(task -> task.status() == TaskStatus.DEPLOYED).status())
              .isEqualTo(TaskStatus.DEPLOYED);
          assertThat(E2eWorkspace.git(flow.origin(), "log", "-1", "--format=%s", "dev"))
              .contains("Merge branch '" + TASK + "' into dev");
        }

        @Test
        void answersQuestionAtDeployConflictUnderTheHandOffItLeavesStanding() throws Exception {
          flow.judges(
              judgement()
                  .withFindings(
                      List.of(new Finding("", "keep both lines", "decided", "advice", "", "")))
                  .build());

          flow.mcp(
              """
              {"jsonrpc": "2.0", "id": 1, "method": "tools/call",
               "params": {"name": "update_agent_status",
                          "arguments": {"status": "IN_PROGRESS", "message": "keep both lines?", "why": "a conflict needs a call",
                                        "outcome": "question"}}}\
              """);
          flow.awaitRun("master-answer");

          flow.awaitTold("keep both lines");
          assertThat(flow.taskContext()).contains("did not deploy");
          assertThat(flow.task().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
        }

        @Nested
        class Stalled {

          @BeforeEach
          void theConflictStallsAfterOneMinute() throws Exception {
            E2eWorkspace.stallConflictsAfterMinutes(flow.configFile(), 1);
          }

          @Test
          void asksTheMasterFirstWhenConflictStallsAndRelaysItsAnswerToTheSession()
              throws Exception {
            flow.judges(
                judgement()
                    .withFindings(
                        List.of(new Finding("", "keep both lines", "decided", "advice", "", "")))
                    .build());

            Instant later = Instant.now(Clock.systemUTC()).plus(Duration.ofMinutes(2));
            doReturn(later).when(clock).instant();
            doReturn(later.toEpochMilli()).when(clock).millis();
            flow.awaitRun("deploy-conflict");
            flow.awaitRun("master-answer");

            flow.awaitTold("keep both lines");
            assertThat(flow.task().currentConflict().filter(Conflict::askedTheMaster)).isPresent();
            assertThat(flow.task().currentConflict().map(Conflict::told).isPresent()).isFalse();
            verify(userNotifier, never()).notify(contains("deploy conflict stalled"), any(), any());
            assertThat(flow.task().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
          }

          @Test
          void leavesStalledConflictToTheHumanWhenTheMastersAnswerIsUnusable() throws Exception {
            Instant later = Instant.now(Clock.systemUTC()).plus(Duration.ofMinutes(2));
            doReturn(later).when(clock).instant();
            doReturn(later.toEpochMilli()).when(clock).millis();
            flow.awaitRun("deploy-conflict");

            flow.awaitRun("master-answer");

            flow.awaitTask(task -> !task.masterOrNone().unanswered().isEmpty());
            assertThat(flow.task().masterOrNone().unanswered())
                .isEqualTo(flow.task().askedDigest());
            assertThat(flow.task().message()).startsWith("The deploy conflict stalled");
            assertThat(flow.task().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
          }

          @Test
          void tellsTheHumanOnceConflictStallsAgainAfterTheMasterWasAskedAndPressesNothing()
              throws Exception {
            Instant later = Instant.now(Clock.systemUTC()).plus(Duration.ofMinutes(2));
            doReturn(later).when(clock).instant();
            doReturn(later.toEpochMilli()).when(clock).millis();
            flow.awaitRun("deploy-conflict");
            flow.awaitTask(
                task -> task.currentConflict().filter(Conflict::askedTheMaster).isPresent());

            Instant muchLater = Instant.now(Clock.systemUTC()).plus(Duration.ofMinutes(4));
            doReturn(muchLater).when(clock).instant();
            doReturn(muchLater.toEpochMilli()).when(clock).millis();
            flow.awaitRun("deploy-conflict");

            assertThat(flow.task().currentConflict().filter(conflict -> conflict.told() != null))
                .isPresent();

            verify(userNotifier, timeout(10_000).times(1))
                .notify(contains("deploy conflict stalled"), any(), any());
            assertThat(flow.task().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
            assertThat(E2eWorkspace.git(flow.origin(), "log", "-1", "--format=%s", "dev"))
                .contains("Someone else changed widget.txt");
          }
        }
      }
    }
  }

  @Nested
  class OwingVerification {

    @BeforeEach
    void theTaskHoldsItsFirstRoundAndItsVerificationFails() throws Exception {
      E2eWorkspace.writeConfig(
          flow.configFile(), flow.repo(), MasterModeCase.rounds().findFirst().orElseThrow());
      E2eWorkspace.failVerificationWith(flow.configFile(), "echo widget broken >&2; exit 1");
      flow.judges(judgement().build());
      provisioning.initializeTask(newTask().build());
      Files.writeString(flow.worktree().resolve("widget.txt"), "fixed\n");
      Files.writeString(flow.worktree().resolve("task_notes.md"), "widget fixed\n");
    }

    @Test
    void sendsRedVerificationBackToTheSession() throws Exception {
      flow.agentReports("REVIEW_PENDING", "VERIFYING", "widget fixed", "");
      flow.awaitRun("verify");

      flow.awaitTold("jagt ran this project's verification command and it failed");
      assertThat(flow.awaitTask(task -> task.status() == TaskStatus.IN_PROGRESS).history())
          .extracting(StatusChange::status)
          .contains(TaskStatus.VERIFYING)
          .doesNotContain(TaskStatus.REVIEW_PENDING);
      assertThat(flow.taskContext())
          .contains("[proj] `sh -c echo widget broken >&2; exit 1` exited 1\nwidget broken");
      assertThat(flow.asked()).isFalse();
    }

    @Test
    void stopsTaskHandedBackOnTheTreeItsVerificationFailedOn() throws Exception {
      flow.agentReports("REVIEW_PENDING", "VERIFYING", "widget fixed", "");
      flow.awaitRun("verify");
      flow.awaitVerificationSentBack();
      Files.writeString(flow.worktree().resolve("task_notes.md"), "widget fixed again\n");

      flow.agentReports("REVIEW_PENDING", "VERIFYING", "widget fixed again", "");
      flow.awaitRun("verify");

      assertThat(Objects.requireNonNull(flow.awaitTask(TaskState::stopped).stop()).trigger())
          .isEqualTo(TaskStop.Trigger.UNCHANGED_FAILURE);
      flow.awaitMasterStop();
      assertThat(flow.task().status()).isEqualTo(TaskStatus.VERIFYING);
      assertThat(flow.reviewerPrompts())
          .singleElement()
          .asString()
          .contains("which jagt stopped because it suspects a loop");
    }

    @Test
    void stopsTaskWhoseVerificationRoundIsSentBackPastTheCeiling() throws Exception {
      E2eWorkspace.stopRoundsOfOneStepAfter(flow.configFile(), 1);
      flow.agentReports("REVIEW_PENDING", "VERIFYING", "widget fixed", "");
      flow.awaitRun("verify");
      flow.awaitVerificationSentBack();
      Files.writeString(flow.worktree().resolve("README.md"), "e2e fixture, edited\n");
      Files.writeString(flow.worktree().resolve("task_notes.md"), "widget fixed another way\n");

      flow.agentReports("REVIEW_PENDING", "VERIFYING", "widget fixed another way", "");
      flow.awaitRun("verify");

      assertThat(Objects.requireNonNull(flow.awaitTask(TaskState::stopped).stop()).trigger())
          .isEqualTo(TaskStop.Trigger.ROUNDS);
      flow.awaitMasterStop();
      assertThat(flow.task().status()).isEqualTo(TaskStatus.VERIFYING);
      assertThat(flow.reviewerPrompts())
          .singleElement()
          .asString()
          .contains("which jagt stopped because it suspects a loop");
    }
  }
}

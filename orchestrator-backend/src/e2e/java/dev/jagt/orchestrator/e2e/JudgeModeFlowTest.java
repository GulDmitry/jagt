package dev.jagt.orchestrator.e2e;

import static dev.jagt.orchestrator.e2e.TestJudgementBuilder.judgement;
import static dev.jagt.orchestrator.e2e.TestNewTaskBuilder.newTask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.service.Assistants;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@E2eFlow(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JudgeModeFlowTest {

  private static final String TASK = "ABC-7";

  @TempDir static Path workspace;

  @DynamicPropertySource
  static void orchestratorLivesInTheTempWorkspace(DynamicPropertyRegistry registry) {
    FlowHarness.livesIn(registry, workspace);
  }

  @MockitoBean(answers = RETURNS_DEEP_STUBS)
  private Assistants assistants;

  @MockitoBean private McpHealth mcp;
  private final TaskProvisioning provisioning;
  private final FlowHarness flow;

  @Autowired
  JudgeModeFlowTest(
      TaskProvisioning provisioning, ApplicationContext context, @LocalServerPort int port) {
    this.provisioning = provisioning;
    this.flow = new FlowHarness(context, workspace, TASK, port);
  }

  @BeforeAll
  static void createTheThrowawayOutsideWorld() throws Exception {
    FlowHarness.createThrowawayWorld(workspace, true);
  }

  @BeforeEach
  void oneProjectAndNoMasterModeWritten() throws Exception {
    E2eWorkspace.writeConfig(flow.configFile(), flow.repo(), "shared", false);
  }

  @AfterEach
  void leaveNothingBehindForTheNextFlow() {
    flow.leaveNothingBehind();
  }

  @Test
  void leavesTheSessionsOwnQuestionForTheHuman() throws Exception {
    provisioning.initializeTask(newTask().build());

    flow.agentReports("IN_PROGRESS", "keep v2?", ", \"outcome\": \"question\"");
    flow.awaitRun("master-answer");
    flow.awaitRun("master-review");

    assertThat(flow.task().message()).contains("keep v2?");
    verify(assistants.reviewer(), never()).review(any());
  }

  @Nested
  class WithTheWidgetFixed {

    @BeforeEach
    void theTaskHoldsItsFirstRound() throws Exception {
      provisioning.initializeTask(newTask().build());
      Files.writeString(flow.worktree().resolve("widget.txt"), "fixed\n");
      Files.writeString(flow.worktree().resolve("task_notes.md"), "widget fixed\n");
    }

    @Test
    void readsReadyRoundAndLeavesEveryPressToTheHuman() throws Exception {
      flow.judges(judgement().build());

      flow.agentReports("REVIEW_PENDING", "widget fixed", "");

      assertThat(flow.awaitReview()).contains("VERDICT: ready");
      flow.awaitRun("master-review");
      assertThat(flow.task().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
      assertThat(flow.task().history())
          .extracting(StatusChange::status)
          .doesNotContain(TaskStatus.SHIPPING);
    }

    @Test
    void sendsNotReadyRoundBackToTheSessionThatWroteIt() throws Exception {
      flow.judges(
          judgement()
              .withVerdict("not ready")
              .withFindings(
                  List.of(
                      new Finding(
                          "widget.txt",
                          "the guard is inverted",
                          "inverted guard",
                          "blocking",
                          "",
                          "")))
              .build());

      flow.agentReports("REVIEW_PENDING", "widget fixed", "");

      flow.awaitTold("the guard is inverted");
      assertThat(flow.awaitTask(task -> task.status() == TaskStatus.IN_PROGRESS).status())
          .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void putsTheReviewersQuestionToTheHumanRatherThanAnsweringIt() throws Exception {
      flow.judges(judgement().withVerdict("question").withQuestion("keep v2 beside v3?").build());

      flow.agentReports("REVIEW_PENDING", "widget fixed", "");

      assertThat(
              flow.awaitTask(task ->
                      task.message() != null && task.message().contains("keep v2 beside v3?"))
                  .report())
          .isEqualTo(AgentReport.QUESTION);
    }

    @Test
    void shipsAndDeploysOnlyWhenTheHumanPresses() throws Exception {
      flow.judges(judgement().build());
      flow.agentReports("REVIEW_PENDING", "widget fixed", "");
      flow.awaitReview();

      assertThat(flow.act("ship")).contains("relayed to the agent");
      flow.pushesTheRoundAndReportsItsRequest();
      flow.act("sweep");
      assertThat(flow.task().status()).isEqualTo(TaskStatus.REVIEWED);
      flow.awaitRun("master-deploy");
      assertThat(flow.task().status()).isEqualTo(TaskStatus.REVIEWED);

      assertThat(flow.act("deploy")).contains("Merged " + TASK + " into dev", "DEPLOYED");
    }
  }
}

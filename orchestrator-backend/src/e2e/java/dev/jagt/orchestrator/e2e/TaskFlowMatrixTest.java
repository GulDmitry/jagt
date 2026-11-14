package dev.jagt.orchestrator.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.capability.done.TaskRetirement;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.launch.NewTask;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@E2eFlow
class TaskFlowMatrixTest {

  @TempDir static Path workspace;

  @DynamicPropertySource
  static void orchestratorLivesInTheTempWorkspace(DynamicPropertyRegistry registry) {
    FlowHarness.livesIn(registry, workspace);
  }

  private final Jobs jobs;
  private final TaskProvisioning provisioning;
  private final TaskRetirement retirement;
  private final StateService stateService;
  private final OrchestratorPaths paths;
  private final ToolchainProperties properties;

  @Autowired
  TaskFlowMatrixTest(
      Jobs jobs,
      TaskProvisioning provisioning,
      TaskRetirement retirement,
      StateService stateService,
      OrchestratorPaths paths,
      ToolchainProperties properties) {
    this.jobs = jobs;
    this.provisioning = provisioning;
    this.retirement = retirement;
    this.stateService = stateService;
    this.paths = paths;
    this.properties = properties;
  }

  @BeforeAll
  static void createTheThrowawayOutsideWorld() throws Exception {
    FlowHarness.createThrowawayWorld(workspace, false);
  }

  @BeforeEach
  void startFromNoneOfThisRunsSessions() {
    E2eWorkspace.killTmuxSessions(Objects.requireNonNull(properties.tmuxCommand()));
  }

  @AfterEach
  void leaveNothingBehindForTheNextCombinationEvenOneThatFailedEarly() {
    E2eWorkspace.forgetTask(workspace.resolve("proj"), workspace.resolve("ABC-1-proj"), "ABC-1");
    E2eWorkspace.killTmuxSessions(Objects.requireNonNull(properties.tmuxCommand()));
    stateService.removeTask("ABC-1");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("dev.jagt.orchestrator.e2e.TaskFlowCase#matrix")
  void taskIsCreatedProvisionedAndThenTornDownLeavingOnlyItsBranch(TaskFlowCase flowCase)
      throws Exception {
    E2eWorkspace.writeConfig(paths.configFile(), workspace.resolve("proj"), flowCase);
    Path worktree = workspace.resolve("ABC-1-proj");

    String created =
        provisioning.initializeTask(
            NewTask.builder("ABC-1", "proj")
                .instructions("Fix the widget")
                .title("Widget layout is off")
                .build());

    E2eWorkspace.awaitFullRunStartedAfterNow(jobs, "master-feed");
    assertThat(created).contains("ABC-1 is a1", "agent running on ABC-1");
    assertThat(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE)).exists();
    assertThat(worktree.resolve("task_context.md")).exists();
    assertThat(worktree.resolve("mcp_client.js")).doesNotExist();
    assertThat(worktree.resolve(".mcp.json")).doesNotExist();
    assertThat(worktree.resolve(".claude")).doesNotExist();
    assertThat(E2eWorkspace.tmuxSessions(Objects.requireNonNull(properties.tmuxCommand())))
        .containsExactly(flowCase.agentSession());
    assertThat(stateService.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.NEW);
    assertThat(stateService.task("ABC-1").orElseThrow().autoReview())
        .isEqualTo(flowCase.autoReview());
    assertThat(E2eWorkspace.git(workspace.resolve("proj"), "branch", "--list", "ABC-1"))
        .contains("ABC-1");

    retirement.retire("ABC-1");

    assertThat(worktree).doesNotExist();
    assertThat(stateService.task("ABC-1")).isEmpty();
    assertThat(E2eWorkspace.git(workspace.resolve("proj"), "branch", "--list", "ABC-1"))
        .contains("ABC-1");
  }
}

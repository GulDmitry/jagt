package dev.jagt.orchestrator.e2e;

import static dev.jagt.orchestrator.e2e.TestNewTaskBuilder.newTask;
import static dev.jagt.orchestrator.e2e.TestReviewFactsBuilder.reviewFacts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.flow.view.TaskView;
import dev.jagt.orchestrator.job.deploy.DeployOnApprovalJob;
import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.service.assistant.Assistants;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.task.TaskViews;
import dev.jagt.orchestrator.surface.mcp.refusal.MasterToken;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.launch.NewTask;
import dev.jagt.orchestrator.task.launch.TicketFacts;
import dev.jagt.orchestrator.task.review.MergeRequestFacts;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.StatusChange;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@E2eFlow(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockitoSpyBean(types = Clock.class)
class ReviewAndDeployFlowTest extends FlowHarness.World {

  private static final String TASK = "ABC-1";
  private static final String TITLE = "Widget layout is off";

  @MockitoBean(answers = RETURNS_DEEP_STUBS)
  private Assistants assistants;

  @MockitoBean private McpHealth mcp;
  private final TaskProvisioning provisioning;
  private final StateService stateService;
  private final TaskStops stops;
  private final TaskViews views;
  private final DeployOnApprovalJob deployOnApproval;
  private final OrchestratorPaths paths;
  private final ToolchainProperties properties;
  private final FlowHarness flow;

  @Autowired
  ReviewAndDeployFlowTest(
      TaskProvisioning provisioning,
      StateService stateService,
      TaskStops stops,
      TaskViews views,
      DeployOnApprovalJob deployOnApproval,
      ApplicationContext context,
      @LocalServerPort int port) {
    this.provisioning = provisioning;
    this.stateService = stateService;
    this.stops = stops;
    this.views = views;
    this.deployOnApproval = deployOnApproval;
    this.paths = context.getBean(OrchestratorPaths.class);
    this.properties = context.getBean(ToolchainProperties.class);
    this.flow = new FlowHarness(context, workspace, TASK, port);
  }

  @BeforeEach
  void oneProject() throws Exception {
    E2eWorkspace.writeConfig(paths.configFile(), flow.repo(), "shared", false);
  }

  @AfterEach
  void leaveNothingBehindForTheNextFlow() {
    stateService.removeTask(TASK);
    E2eWorkspace.forgetTask(flow.repo(), flow.worktree(), TASK);
    E2eWorkspace.forgetTask(flow.webRepo(), flow.webWorktree(), TASK);
    E2eWorkspace.resetDeployBranch(flow.repo());
    E2eWorkspace.resetDeployBranch(flow.webRepo());
    E2eWorkspace.killTmuxSessions(Objects.requireNonNull(properties.tmuxCommand()));
  }

  @Test
  void opensNoRequestInTheRepositoryTheTaskNeverChanged() throws Exception {
    E2eWorkspace.writeConfig(
        paths.configFile(),
        new LinkedHashMap<>(Map.of("proj", flow.repo(), "web", flow.webRepo())),
        "shared",
        false);

    provisioning.initializeTask(
        NewTask.builder(TASK, "proj")
            .alsoIn(List.of("web"))
            .instructions("Move the api side")
            .title(TITLE)
            .build());
    Files.writeString(flow.worktree().resolve("widget.txt"), "api side\n");
    Files.writeString(flow.worktree().resolve("task_notes.md"), "api side done\n");
    flow.agentReports("REVIEW_PENDING", "api side done", "");

    String shipped = flow.act("ship");

    assertThat(shipped).contains("nothing to ship in web");
    assertThat(Files.readString(flow.worktree().resolve("task_context.md")))
        .contains("proj: " + flow.worktree() + ", merges into main")
        .contains("reviewRequests={\"proj\": \"<its url>\"}")
        .doesNotContain("web: " + flow.webWorktree());
  }

  @Nested
  class AcrossBothRepositories {

    @BeforeEach
    void theTaskSpansBothRepositories() throws Exception {
      E2eWorkspace.writeConfig(
          paths.configFile(),
          new LinkedHashMap<>(Map.of("proj", flow.repo(), "web", flow.webRepo())),
          "shared",
          false);
      provisioning.initializeTask(
          newTask()
              .withTask(TASK)
              .withProjects(List.of("proj", "web"))
              .withInstructions("Move both sides of the contract")
              .build());
      Files.writeString(flow.worktree().resolve("widget.txt"), "api side\n");
      Files.writeString(flow.webWorktree().resolve("widget.txt"), "web side\n");
      when(assistants.codeHost().readReview(flow.request()))
          .thenReturn(
              new Answer<>(Optional.of(reviewFacts().withApproved(true).build()), TokenUsage.NONE));
      when(assistants.codeHost().readReview(flow.webRequest()))
          .thenReturn(
              new Answer<>(Optional.of(reviewFacts().withApproved(true).build()), TokenUsage.NONE));
    }

    @Test
    void stopsAtTheRepositoryThatConflictsAndFinishesItOnTheNextDeploy() throws Exception {
      Files.writeString(flow.worktree().resolve("task_notes.md"), "both sides done\n");
      flow.agentReports("REVIEW_PENDING", "both sides done", "");
      flow.act("ship");
      flow.agentCommitsAndPushes(flow.worktree(), TASK + " " + TITLE);
      flow.agentCommitsAndPushes(flow.webWorktree(), TASK + " " + TITLE);
      flow.agentReports(
          "CI_POLLING",
          "requests up",
          ", \"reviewRequests\": {\"proj\": \""
              + flow.request()
              + "\", \"web\": \""
              + flow.webRequest()
              + "\"}");
      flow.act("sweep");
      E2eWorkspace.commitOnDeployBranch(flow.webRepo(), "widget.txt", "someone else's line\n");

      String conflicted = flow.act("deploy");

      assertThat(conflicted)
          .contains(
              "CONFLICT merging web into dev",
              "Live on the deploy branch: proj",
              "NOT deployed: web");
      assertThat(flow.task().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
      assertThat(E2eWorkspace.git(flow.origin(), "log", "-1", "--format=%s", "dev"))
          .contains("Merge branch '" + TASK + "' into dev");
      assertThat(E2eWorkspace.git(flow.webOrigin(), "log", "-1", "--format=%s", "dev"))
          .doesNotContain("Merge branch '" + TASK + "' into dev");

      Path resolveIn = flow.webWorktree().resolveSibling("web-deploy");
      Files.writeString(resolveIn.resolve("widget.txt"), "both sides, resolved\n");
      E2eWorkspace.git(resolveIn, "add", "widget.txt");

      assertThat(flow.act("deploy")).contains("DEPLOYED");
      assertThat(E2eWorkspace.git(flow.webOrigin(), "log", "-1", "--format=%s", "dev"))
          .contains("Merge branch '" + TASK + "' into dev");
      assertThat(flow.task().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void oneSessionShipsAndDeploysEveryRepositoryTheTaskSpansAndTakesThemBackOutInReverse()
        throws Exception {
      Files.writeString(flow.worktree().resolve("task_notes.md"), "both sides done\n");
      flow.agentReports("REVIEW_PENDING", "both sides done", "");

      String shipped = flow.act("ship");

      assertThat(shipped).contains("relayed to the agent", "requests");
      assertThat(Files.readString(flow.worktree().resolve("task_context.md")))
          .contains(
              "proj: " + flow.worktree() + ", merges into main",
              "web: " + flow.webWorktree() + ", merges into main",
              "reviewRequests={\"proj\": \"<its url>\", \"web\": \"<its url>\"}");
      flow.agentCommitsAndPushes(flow.worktree(), TASK + " " + TITLE);
      flow.agentCommitsAndPushes(flow.webWorktree(), TASK + " " + TITLE);
      flow.agentReports(
          "CI_POLLING",
          "requests up",
          ", \"reviewRequests\": {\"proj\": \""
              + flow.request()
              + "\", \"web\": \""
              + flow.webRequest()
              + "\"}");
      assertThat(E2eWorkspace.git(flow.webOrigin(), "log", "-1", "--format=%s", TASK))
          .contains(TASK + " " + TITLE);
      assertThat(flow.task().repos())
          .extracting(dev.jagt.orchestrator.task.repo.TaskRepo::mrUrl)
          .containsExactly(flow.request(), flow.webRequest());
      assertThat(flow.task().history())
          .extracting(StatusChange::status)
          .containsExactly(
              TaskStatus.NEW,
              TaskStatus.REVIEW_PENDING,
              TaskStatus.SHIPPING,
              TaskStatus.CI_POLLING);

      flow.act("sweep");
      assertThat(flow.task().status()).isEqualTo(TaskStatus.APPROVED);

      assertThat(flow.act("deploy")).contains("proj into dev", "web into dev", "DEPLOYED");
      assertThat(E2eWorkspace.git(flow.origin(), "log", "-1", "--format=%s", "dev"))
          .contains("Merge branch '" + TASK + "' into dev");
      assertThat(E2eWorkspace.git(flow.webOrigin(), "log", "-1", "--format=%s", "dev"))
          .contains("Merge branch '" + TASK + "' into dev");

      assertThat(flow.act("revert")).contains("reverted web on dev", "proj on dev", "REVERTED");
      assertThat(E2eWorkspace.git(flow.webOrigin(), "log", "-1", "--format=%s", "dev"))
          .contains("Revert \"Merge branch '" + TASK + "' into dev\"");
    }
  }

  @Test
  void resumeAdoptsAnOpenRequestOnTheBranchThatIsAlreadyThere() throws Exception {
    E2eWorkspace.git(flow.repo(), "branch", TASK, "main");
    when(assistants.codeHost().readMergeRequest(flow.request()))
        .thenReturn(
            new Answer<>(
                Optional.of(new MergeRequestFacts(true, TASK, "main", TASK + " " + TITLE)),
                TokenUsage.NONE));
    when(assistants.tracker().readTicket(eq(TASK), any()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    TicketFacts.defaults()
                        .withExists(true)
                        .withKey(TASK)
                        .withTitle(TITLE)
                        .withTrackerProject("ABC")
                        .withUrl("https://tracker.example.com/" + TASK)),
                TokenUsage.NONE));

    String resumed =
        flow.post(
            "/api/commands/resume?about="
                + java.net.URLEncoder.encode(
                    flow.request(), java.nio.charset.StandardCharsets.UTF_8),
            "",
            Map.of());

    assertThat(resumed).contains("Resumed " + TASK + " on its existing branch", "CI_POLLING");
    assertThat(flow.task().status()).isEqualTo(TaskStatus.CI_POLLING);
    assertThat(flow.task().mrUrl()).isEqualTo(flow.request());
    assertThat(flow.task().baseBranch()).isEqualTo("main");
    assertThat(flow.task().title()).isEqualTo(TITLE);
    assertThat(flow.worktree().resolve("task_context.md")).exists();
  }

  @Test
  void taskOpenedOnTheBoardRecordsTheBoardAsItsOpener() throws Exception {
    when(assistants.tracker().readTicket(eq(TASK), any()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    TicketFacts.defaults()
                        .withExists(true)
                        .withKey(TASK)
                        .withTitle(TITLE)
                        .withTrackerProject("ABC")
                        .withUrl("https://tracker.example.com/" + TASK)),
                TokenUsage.NONE));

    flow.post("/api/tasks", "{\"ref\": \"" + TASK + "\", \"project\": \"proj\"}", Map.of());

    assertThat(flow.task().history())
        .extracting(StatusChange::status, StatusChange::origin)
        .containsExactly(tuple(TaskStatus.NEW, ActionOrigin.BOARD));
  }

  @Test
  void taskOpenedByTheRootSessionRecordsTheRootAsItsOpener() throws Exception {
    String call =
        """
        {"jsonrpc": "2.0", "id": 1, "method": "tools/call",
         "params": {"name": "initialize_task", "arguments": {"taskId": "%s", "projectKey": "proj", "why": "user asked for it"}}}\
        """
            .formatted(TASK);

    flow.post("/mcp", call, Map.of(MasterToken.HEADER, Files.readString(paths.token()).strip()));

    assertThat(flow.task().history())
        .extracting(StatusChange::status, StatusChange::origin)
        .containsExactly(tuple(TaskStatus.NEW, ActionOrigin.ROOT));
  }

  @Nested
  class AfterTheFirstRoundShipped {

    private String shipped;

    @BeforeEach
    void theAgentHandsBackAndThePushedRoundOpensItsRequest() throws Exception {
      provisioning.initializeTask(
          NewTask.builder(TASK, "proj").instructions("Fix the widget").title(TITLE).build());
      Files.writeString(flow.worktree().resolve("widget.txt"), "fixed\n");
      Files.writeString(flow.worktree().resolve("task_notes.md"), "widget fixed\n");
      flow.agentReports("REVIEW_PENDING", "widget fixed", "");
      shipped = flow.act("ship");
      flow.agentCommitsAndPushes(flow.worktree(), TASK + " " + TITLE);
      flow.agentReports(
          "CI_POLLING",
          "review request: " + flow.request(),
          ", \"reviewRequestUrl\": \"" + flow.request() + "\"");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("dev.jagt.orchestrator.e2e.ReviewRoundCase#matrix")
    void sweptRoundEndsWhereItsFactsSayAndBriefsTheAgentOnlyWhenThereIsWork(ReviewRoundCase round)
        throws Exception {
      when(assistants.codeHost().readReview(flow.request()))
          .thenReturn(new Answer<>(Optional.ofNullable(round.round()), TokenUsage.NONE));

      assertThat(flow.act("sweep")).contains(round.sentence());

      assertThat(flow.task().status()).isEqualTo(round.expected());
      assertThat(
              Files.readString(flow.worktree().resolve("task_context.md"))
                  .contains("Review round for"))
          .as("the agent was handed this round")
          .isEqualTo(round.briefed());
    }

    @Test
    void roundTripFromShipToDeployAndBackOutAgain() throws Exception {
      assertThat(shipped).contains("relayed to the agent", "SHIPPING");
      assertThat(Files.readString(flow.worktree().resolve("task_context.md")))
          .contains(
              "proj: " + flow.worktree() + ", merges into main",
              "EXACTLY \"" + TASK + " " + TITLE + "\"",
              "reviewRequestUrl=<the url>");
      assertThat(flow.task().mrUrl()).isEqualTo(flow.request());
      assertThat(E2eWorkspace.git(flow.origin(), "log", "-1", "--format=%s", TASK))
          .contains(TASK + " " + TITLE);

      when(assistants.codeHost().readReview(flow.request()))
          .thenReturn(
              new Answer<>(
                  Optional.of(
                      reviewFacts()
                          .withThreads(List.of("bot (Widget.java:12): tighten this"))
                          .build()),
                  TokenUsage.NONE));
      flow.act("sweep");
      assertThat(Files.readString(flow.worktree().resolve("task_context.md")))
          .contains("bot (Widget.java:12): tighten this", "Do NOT push or post anything yourself");

      Files.writeString(flow.worktree().resolve("widget.txt"), "tightened\n");
      Files.writeString(flow.worktree().resolve("review_replies.md"), "> tighten this\n\nDone.\n");
      Files.writeString(flow.worktree().resolve("task_notes.md"), "comment addressed\n");
      flow.agentReports("REVIEW_PENDING", "comment addressed", "");

      assertThat(flow.act("ship")).contains("relayed to the agent");
      assertThat(Files.readString(flow.worktree().resolve("task_context.md")))
          .contains(
              "its request is already open",
              "do NOT create another or retitle it",
              "post each drafted reply to its thread");
      flow.agentCommitsAndPushes(flow.worktree(), TASK + " address review comments");
      flow.agentReports(
          "CI_POLLING",
          "review request: " + flow.request(),
          ", \"reviewRequestUrl\": \"" + flow.request() + "\"");
      assertThat(E2eWorkspace.git(flow.origin(), "log", "-1", "--format=%s", TASK))
          .contains(TASK + " address review comments");

      when(assistants.codeHost().readReview(flow.request()))
          .thenReturn(
              new Answer<>(Optional.of(reviewFacts().withApproved(true).build()), TokenUsage.NONE));
      assertThat(flow.act("sweep")).contains("approved, checks success");
      assertThat(flow.task().status()).isEqualTo(TaskStatus.APPROVED);

      assertThat(flow.act("deploy")).contains("Merged " + TASK + " into dev", "DEPLOYED");
      assertThat(
              Objects.requireNonNull(flow.task().deployment())
                  .targets()
                  .getFirst()
                  .landing()
                  .merge())
          .isNotBlank();
      assertThat(E2eWorkspace.git(flow.origin(), "log", "-1", "--format=%s", "dev"))
          .contains("Merge branch '" + TASK + "' into dev");

      assertThat(flow.act("revert")).contains("Reverted " + TASK + " on dev", "REVERTED");
      assertThat(E2eWorkspace.git(flow.origin(), "log", "-1", "--format=%s", "dev"))
          .contains("Revert \"Merge branch '" + TASK + "' into dev\"");

      assertThat(flow.task().history())
          .extracting(StatusChange::status)
          .containsExactly(
              TaskStatus.NEW,
              TaskStatus.REVIEW_PENDING,
              TaskStatus.SHIPPING,
              TaskStatus.CI_POLLING,
              TaskStatus.REVIEW_PENDING,
              TaskStatus.SHIPPING,
              TaskStatus.CI_POLLING,
              TaskStatus.APPROVED,
              TaskStatus.DEPLOYED,
              TaskStatus.REVERTED);
      assertThat(flow.task().history())
          .extracting(StatusChange::status, StatusChange::origin)
          .contains(
              tuple(TaskStatus.REVIEW_PENDING, ActionOrigin.MCP),
              tuple(TaskStatus.DEPLOYED, ActionOrigin.BOARD));

      assertThat(flow.act("done")).contains("worktree deleted", "Branch '" + TASK + "' was kept");
      assertThat(flow.worktree()).doesNotExist();
      assertThat(stateService.task(TASK)).isEmpty();
      assertThat(E2eWorkspace.git(flow.repo(), "branch", "--list", TASK)).contains(TASK);
    }

    @Test
    void approvedRequestDeploysItselfWhenDeployOnApprovalIsOnAndNamesTheCodeHostAsWhoActed()
        throws Exception {
      Files.writeString(
          paths.configFile(), "  deploy: { onApproval: true }\n", StandardOpenOption.APPEND);
      when(assistants.codeHost().readReview(flow.request()))
          .thenReturn(
              new Answer<>(Optional.of(reviewFacts().withApproved(true).build()), TokenUsage.NONE));
      flow.act("sweep");

      deployOnApproval.runOn(stateService.tasks());

      assertThat(flow.task().status()).isEqualTo(TaskStatus.DEPLOYED);
      assertThat(flow.task().history())
          .extracting(StatusChange::status, StatusChange::origin)
          .contains(tuple(TaskStatus.DEPLOYED, ActionOrigin.CODE_HOST));
    }

    @Test
    void followUpRequestAfterMergedOneLiftsTheStopAndIsReadByTheNextSweep(@TempDir Path clone)
        throws Exception {
      for (String round : List.of("second", "third", "fourth")) {
        Files.writeString(flow.worktree().resolve("task_notes.md"), round + " round\n");
        flow.agentReports("REVIEW_PENDING", round + " round", "");
        flow.act("ship");
        flow.agentReports(
            "CI_POLLING",
            "review request: " + flow.request(),
            ", \"reviewRequestUrl\": \"" + flow.request() + "\"");
      }
      Files.writeString(flow.worktree().resolve("task_notes.md"), "fifth round\n");
      flow.agentReports("REVIEW_PENDING", "fifth round", "");
      assertThat(stops.mayShip(TASK)).isFalse();
      assertThat(Objects.requireNonNull(flow.task().stop()).trigger())
          .isEqualTo(TaskStop.Trigger.SHIPS);
      assertThat(flow.task().stop().request()).isEqualTo(flow.request());
      assertThat(
              views.all().stream()
                  .filter(view -> view.id().equals(TASK))
                  .findFirst()
                  .orElseThrow()
                  .actions()
                  .stream()
                  .map(TaskView.ActionView::id)
                  .toList())
          .contains("ship");
      long ships =
          flow.task().history().stream()
              .filter(step -> step.status() == TaskStatus.SHIPPING)
              .count();
      String main = E2eWorkspace.git(flow.origin(), "rev-parse", "main").strip();

      try {
        E2eWorkspace.git(clone, "clone", "-q", E2eWorkspace.remoteUrl(flow.origin()), ".");
        E2eWorkspace.git(clone, "config", "user.email", "e2e@example.com");
        E2eWorkspace.git(clone, "config", "user.name", "jagt e2e");
        E2eWorkspace.git(clone, "merge", "--no-ff", "-m", "Merge " + TASK, "origin/" + TASK);
        E2eWorkspace.git(clone, "push", "-q", "origin", "main");

        assertThat(flow.act("ship")).contains("already merged");
        assertThat(
                flow.task().history().stream()
                    .filter(step -> step.status() == TaskStatus.SHIPPING)
                    .count())
            .isEqualTo(ships);
        assertThat(
                views.all().stream()
                    .filter(view -> view.id().equals(TASK))
                    .findFirst()
                    .orElseThrow()
                    .actions()
                    .stream()
                    .map(TaskView.ActionView::id)
                    .toList())
            .doesNotContain("ship");

        String followUp = flow.request().replace("/merge_requests/1", "/merge_requests/2");
        when(assistants.codeHost().readReview(followUp))
            .thenReturn(new Answer<>(Optional.of(reviewFacts().build()), TokenUsage.NONE));
        flow.agentReports("IN_PROGRESS", "more work after the merge", "");
        Files.writeString(flow.worktree().resolve("widget.txt"), "follow-up\n");
        flow.agentCommitsAndPushes(flow.worktree(), TASK + " follow-up");
        flow.agentReports(
            "CI_POLLING",
            "review request: " + followUp,
            ", \"reviewRequestUrl\": \"" + followUp + "\"");

        assertThat(flow.task().stopped()).isFalse();
        assertThat(stops.mayShip(TASK)).isTrue();
        assertThat(flow.act("sweep")).doesNotContain("stopped");
        verify(assistants.codeHost(), atLeastOnce()).readReview(followUp);
      } finally {
        E2eWorkspace.git(clone, "push", "-q", "--force", "origin", main + ":refs/heads/main");
        E2eWorkspace.git(flow.repo(), "fetch", "-q", "origin");
      }
    }
  }
}

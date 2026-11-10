package dev.jagt.orchestrator.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.Assistants;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;

final class FlowHarness {

  private static final Duration A_FEW_TICKS_AND_A_DEPLOY_TICK = Duration.ofSeconds(55);

  private final HttpClient client = HttpClient.newHttpClient();
  private final ApplicationContext context;
  private final Path workspace;
  private final String task;
  private final int port;

  FlowHarness(ApplicationContext context, Path workspace, String task, int port) {
    this.context = context;
    this.workspace = workspace;
    this.task = task;
    this.port = port;
  }

  static void livesIn(DynamicPropertyRegistry registry, Path workspace) {
    registry.add("orchestrator.root", () -> workspace.resolve("root").toString());
    registry.add("orchestrator.config-file", () -> workspace.resolve("root/jagt.yml").toString());
    registry.add("orchestrator.state-file", () -> workspace.resolve("root/state.json").toString());
  }

  static void createThrowawayWorld(Path workspace, boolean withMasterBrief) throws Exception {
    E2eWorkspace.createRootMarker(workspace.resolve("root"));
    if (withMasterBrief) {
      Files.copy(
          Path.of("../roles/master/brief.md.dist"),
          workspace.resolve("root/roles/master/brief.md.dist"));
    }
    E2eWorkspace.createRepositoryWithOrigin(
        workspace.resolve("origin.git"), workspace.resolve("proj"));
  }

  static void readsGreenReview(Assistants assistants, String request) {
    when(assistants.codeHost().readReview(request))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
  }

  Path configFile() {
    return context.getBean(OrchestratorPaths.class).configFile();
  }

  void writeConfig(MasterModeCase master) throws Exception {
    E2eWorkspace.writeConfig(configFile(), repo(), master);
  }

  void awaitRun(String job) {
    E2eWorkspace.awaitFullRunStartedAfterNow(context.getBean(Jobs.class), job);
  }

  void judges(Judgement judgement) {
    when(context.getBean(Assistants.class).reviewer().review(any()))
        .thenReturn(new Answer<>(Optional.of(judgement), TokenUsage.NONE));
  }

  List<String> reviewerPrompts() {
    return mockingDetails(context.getBean(Assistants.class).reviewer()).getInvocations().stream()
        .map(call -> call.<RoundReviewer.Round>getArgument(0).prompt())
        .toList();
  }

  String taskContext() throws Exception {
    return Files.readString(worktree().resolve("task_context.md"));
  }

  void awaitMasterStop() {
    awaitRun("verify");
    awaitRun("master-stop");
  }

  boolean asked() {
    return !mockingDetails(context.getBean(Assistants.class).reviewer())
        .getInvocations()
        .isEmpty();
  }

  void initializeTask() {
    context
        .getBean(TaskProvisioning.class)
        .initializeTask(
            NewTask.builder(task, "proj")
                .instructions("Fix the widget")
                .title("Widget layout is off")
                .build());
  }

  void handBackTheFirstRound() throws Exception {
    initializeTask();
    Files.writeString(worktree().resolve("widget.txt"), "fixed\n");
    Files.writeString(worktree().resolve("task_notes.md"), "widget fixed\n");
    agentReports("REVIEW_PENDING", "widget fixed", "");
  }

  void startsTaskAcrossBothRepositories() throws Exception {
    E2eWorkspace.writeConfig(
        configFile(),
        new LinkedHashMap<>(Map.of("proj", repo(), "web", workspace.resolve("web"))),
        "shared",
        false);
    context
        .getBean(TaskProvisioning.class)
        .initializeTask(
            NewTask.builder(task, "proj")
                .alsoIn(List.of("web"))
                .instructions("Move both sides of the contract")
                .title("Widget layout is off")
                .build());
    Files.writeString(worktree().resolve("widget.txt"), "api side\n");
    Files.writeString(workspace.resolve(task + "-web").resolve("widget.txt"), "web side\n");
  }

  void handBackOwingVerification() throws Exception {
    judges(new Judgement("", "ready", List.of(), "", List.of(), ""));
    initializeTask();
    Files.writeString(worktree().resolve("widget.txt"), "fixed\n");
    Files.writeString(worktree().resolve("task_notes.md"), "widget fixed\n");
    agentReports("REVIEW_PENDING", "VERIFYING", "widget fixed", "");
    awaitRun("verify");
  }

  void awaitVerificationSentBack() {
    awaitTold("jagt ran this project's verification command and it failed");
    awaitTask(reached -> reached.status() == TaskStatus.IN_PROGRESS);
  }

  void shipsTheRoundTheMasterShipped() throws Exception {
    awaitRun("master-review");
    awaitTask(reached -> reached.status() == TaskStatus.SHIPPING);
    E2eWorkspace.git(worktree(), "add", "widget.txt");
    E2eWorkspace.git(worktree(), "commit", "-m", task + " fix the widget");
    E2eWorkspace.git(worktree(), "push", "-u", "origin", task);
    agentReports(
        "CI_POLLING", "review request up", ", \"reviewRequestUrl\": \"" + request() + "\"");
    readsGreenReview(context.getBean(Assistants.class), request());
  }

  void reachesDeployConflict() throws Exception {
    judges(new Judgement("", "ready", List.of(), "", List.of(), ""));
    handBackTheFirstRound();
    shipsTheRoundTheMasterShipped();
    E2eWorkspace.commitOnDeployBranch(repo(), "widget.txt", "someone else's line\n");
    act("sweep");
    awaitRun("master-deploy");
    awaitTask(reached -> reached.status() == TaskStatus.DEPLOY_CONFLICT);
    awaitRun("deploy-conflict");
    awaitTold("did not deploy");
  }

  void judgesReadyWithAdvice(String advice) {
    judges(
        new Judgement(
            "",
            "ready",
            List.of(new Finding("", advice, "decided", "advice", "", "")),
            "",
            List.of(),
            ""));
  }

  void conflictStallsFor(Clock clock, int minutes) {
    Instant later = Instant.now(Clock.systemUTC()).plus(Duration.ofMinutes(minutes));
    doReturn(later).when(clock).instant();
    doReturn(later.toEpochMilli()).when(clock).millis();
    awaitRun("deploy-conflict");
  }

  void leaveNothingBehind() {
    context.getBean(StateService.class).removeTask(task);
    E2eWorkspace.forgetTask(repo(), worktree(), task);
    E2eWorkspace.resetDeployBranch(repo());
    E2eWorkspace.killTmuxSessions(
        Objects.requireNonNull(context.getBean(ToolchainProperties.class).tmuxCommand()));
  }

  TaskState task() {
    return context.getBean(StateService.class).task(task).orElseThrow();
  }

  TaskState awaitTask(Predicate<TaskState> reached) {
    return await()
        .atMost(A_FEW_TICKS_AND_A_DEPLOY_TICK)
        .pollInterval(Duration.ofMillis(500))
        .until(this::task, reached);
  }

  TaskState settled(MasterModeCase master) {
    awaitTask(reached -> reached.status() == master.expected());
    awaitRun("master-review");
    awaitRun("master-answer");
    return task();
  }

  String awaitReview() throws Exception {
    Path review = worktree().resolve("master-review.md");
    await()
        .atMost(A_FEW_TICKS_AND_A_DEPLOY_TICK)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> Files.exists(review));
    return Files.readString(review);
  }

  void awaitTold(String line) {
    Path taskContext = worktree().resolve("task_context.md");
    await()
        .atMost(A_FEW_TICKS_AND_A_DEPLOY_TICK)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> Files.readString(taskContext).contains(line));
  }

  void agentReports(String status, String message, String extraArgument) throws Exception {
    agentReports(status, status, message, extraArgument);
  }

  void agentReports(String status, String landsAt, String message, String extraArgument)
      throws Exception {
    String call =
        """
        {"jsonrpc": "2.0", "id": 1, "method": "tools/call",
         "params": {"name": "update_agent_status",
                    "arguments": {"status": "%s", "message": "%s", "why": "the session reports"%s}}}\
        """
            .formatted(status, message, extraArgument);
    assertThat(mcp(call)).contains("-> " + landsAt);
  }

  String mcp(String call) throws Exception {
    return post("/mcp", call, Map.of("X-Working-Directory", worktree().toString()));
  }

  String act(String action) throws Exception {
    return post("/api/tasks/actions/" + action + "?task=" + task, "", Map.of());
  }

  String post(String path, String body, Map<String, String> headers) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .header("Origin", "http://127.0.0.1:" + port)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    headers.forEach(request::header);
    HttpResponse<String> answer =
        client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(answer.statusCode()).as("%s", answer.body()).isEqualTo(200);
    return answer.body();
  }

  String request() {
    return E2eWorkspace.requestUrl(origin());
  }

  Path origin() {
    return workspace.resolve("origin.git");
  }

  Path repo() {
    return workspace.resolve("proj");
  }

  Path worktree() {
    return workspace.resolve(task + "-proj");
  }
}

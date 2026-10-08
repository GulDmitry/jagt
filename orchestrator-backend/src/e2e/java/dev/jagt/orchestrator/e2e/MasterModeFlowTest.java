package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.job.IdeRecentProjectsCleaner;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.port.MasterAssistant;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=",
                "orchestrator.agent.cli=stub", "orchestrator.open-terminal-window=false",
                "orchestrator.startup-checks=false"})
class MasterModeFlowTest {

    private static final String TASK = "ABC-7";
    private static final Duration A_FEW_TICKS_AND_A_DEPLOY_TICK = Duration.ofSeconds(55);

    private final HttpClient client = HttpClient.newHttpClient();

    @TempDir
    static Path workspace;

    @DynamicPropertySource
    static void orchestratorLivesInTheTempWorkspace(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> workspace.resolve("root").toString());
        registry.add("orchestrator.config-file", () -> workspace.resolve("root/jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> workspace.resolve("root/state.json").toString());
    }

    @MockitoBean
    private IdeRecentProjectsCleaner ideRecentProjectsCleaner;
    @MockitoBean
    private TerminalDriver terminalDriver;
    @MockitoBean
    private EditorDriver editorDriver;
    @MockitoBean
    private UserNotifier userNotifier;
    @MockitoBean
    private MasterAssistant assistant;
    @MockitoBean
    private RoundReviewer reviewer;
    @Autowired
    private TaskProvisioning provisioning;
    @Autowired
    private StateService stateService;
    @Autowired
    private OrchestratorPaths paths;
    @Autowired
    private OrchestratorProperties properties;
    @Autowired
    private Jobs jobs;
    @LocalServerPort
    private int port;

    @BeforeAll
    static void createTheThrowawayOutsideWorld() throws Exception {
        E2eWorkspace.createRootMarker(workspace.resolve("root"));
        Files.copy(Path.of("../master-brief.md.dist"), workspace.resolve("root/master-brief.md.dist"));
        E2eWorkspace.createRepositoryWithOrigin(workspace.resolve("origin.git"), workspace.resolve("proj"));
    }

    @AfterEach
    void leaveNothingBehindForTheNextFlow() {
        stateService.removeTask(TASK);
        E2eWorkspace.forgetTask(repo(), worktree(), TASK);
        E2eWorkspace.resetDeployBranch(repo());
        E2eWorkspace.killTmuxSessions(properties.tmuxCommand());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.jagt.orchestrator.e2e.MasterModeCase#rounds")
    void readsAndPressesARoundAsTheModeAllows(MasterModeCase master) throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), repo(), master);
        judges(new Judgement("", master.verdict(), List.of(), "", List.of()));

        handBackTheFirstRound();

        assertThat(settled(master).status()).isEqualTo(master.expected());
        assertThat(asked()).isEqualTo(master.read());
        assertThat(Files.readString(worktree().resolve("task_context.md"))).contains(master.told());
    }

    @Test
    void stampsTheShipItPressesAsTheMastersOwn() throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), repo(),
                MasterModeCase.rounds().findFirst().orElseThrow());
        judges(new Judgement("", "ready", List.of(), "", List.of()));

        handBackTheFirstRound();

        assertThat(awaitTask(task -> task.status() == TaskStatus.SHIPPING).history())
                .filteredOn(change -> change.status() == TaskStatus.SHIPPING)
                .extracting(StatusChange::origin).containsExactly(ActionOrigin.MASTER);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.jagt.orchestrator.e2e.MasterModeCase#plans")
    void readsAndStartsAPlanAsTheModeAllows(MasterModeCase master) throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), repo(), master);
        judges(new Judgement("", master.verdict(), List.of(), "", List.of()));
        provisioning.initializeTask(NewTask.builder(TASK, "proj").instructions("Fix the widget")
                .title("Widget layout is off").build());
        Files.writeString(worktree().resolve("plan.md"), "Move the widget left.\n");

        agentReports("PLAN_PENDING", "plan written", "");

        assertThat(settled(master).status()).isEqualTo(master.expected());
        assertThat(asked()).isEqualTo(master.read());
        assertThat(Files.readString(worktree().resolve("task_context.md"))).contains(master.told());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.jagt.orchestrator.e2e.MasterModeCase#questions")
    void answersASessionsQuestionAsTheModeAllows(MasterModeCase master) throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), repo(), master);
        judges(new Judgement("", "ready",
                List.of(new Finding("", "keep v2 beside v3", "decided", "advice")), "", List.of()));
        provisioning.initializeTask(NewTask.builder(TASK, "proj").instructions("Fix the widget")
                .title("Widget layout is off").build());

        agentReports("IN_PROGRESS", "keep v2?", ", \"outcome\": \"question\"");

        assertThat(settled(master).status()).isEqualTo(master.expected());
        assertThat(asked()).isEqualTo(master.read());
        assertThat(Files.readString(worktree().resolve("task_context.md"))).contains(master.told());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.jagt.orchestrator.e2e.MasterModeCase#deploys")
    void deploysAGreenRequestAsTheModeAllows(MasterModeCase master) throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), repo(), master);
        judges(new Judgement("", "ready", List.of(), "", List.of()));
        handBackTheFirstRound();
        shipsTheRoundTheMasterShipped();

        act("sweep");

        assertThat(awaitTask(task -> task.status() == master.expected()).status()).isEqualTo(master.expected());
        E2eWorkspace.awaitAFullRunStartedAfterNow(jobs, "master-deploy");
        assertThat(task().status()).isEqualTo(master.expected());
    }

    @Test
    void handsADeployConflictToTheSessionAndFinishesTheDeployOnceItIsResolved() throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), repo(), MasterModeCase.deploys().findFirst().orElseThrow());
        judges(new Judgement("", "ready", List.of(), "", List.of()));
        handBackTheFirstRound();
        shipsTheRoundTheMasterShipped();
        E2eWorkspace.commitOnDeployBranch(repo(), "widget.txt", "someone else's line\n");
        act("sweep");
        awaitTask(task -> task.status() == TaskStatus.DEPLOY_CONFLICT);
        awaitTold("did not deploy");

        Path resolveIn = GitDeploy.deployWorktreePath(repo(), TASK);
        Files.writeString(resolveIn.resolve("widget.txt"), "both sides, resolved\n");
        E2eWorkspace.git(resolveIn, "add", "widget.txt");

        assertThat(awaitTask(task -> task.status() == TaskStatus.DEPLOYED).status()).isEqualTo(TaskStatus.DEPLOYED);
        assertThat(E2eWorkspace.git(workspace.resolve("origin.git"), "log", "-1", "--format=%s", "dev"))
                .contains("Merge branch '" + TASK + "' into dev");
    }

    private void judges(Judgement judgement) {
        when(reviewer.review(any())).thenReturn(new Answer<>(Optional.of(judgement), TokenUsage.NONE));
    }

    private boolean asked() {
        return !mockingDetails(reviewer).getInvocations().isEmpty();
    }

    private void handBackTheFirstRound() throws Exception {
        provisioning.initializeTask(NewTask.builder(TASK, "proj").instructions("Fix the widget")
                .title("Widget layout is off").build());
        Files.writeString(worktree().resolve("widget.txt"), "fixed\n");
        Files.writeString(worktree().resolve("task_notes.md"), "widget fixed\n");
        agentReports("REVIEW_PENDING", "widget fixed", "");
    }

    private void shipsTheRoundTheMasterShipped() throws Exception {
        awaitTask(task -> task.status() == TaskStatus.SHIPPING);
        E2eWorkspace.git(worktree(), "add", "widget.txt");
        E2eWorkspace.git(worktree(), "commit", "-m", TASK + " fix the widget");
        E2eWorkspace.git(worktree(), "push", "-u", "origin", TASK);
        agentReports("CI_POLLING", "review request up", ", \"reviewRequestUrl\": \"" + request() + "\"");
        when(assistant.readReview(request()))
                .thenReturn(new Answer<>(Optional.of(new ReviewFacts(true, false, "success", List.of())),
                        TokenUsage.NONE));
    }

    private TaskState settled(MasterModeCase master) {
        awaitTask(task -> task.status() == master.expected());
        E2eWorkspace.awaitAFullRunStartedAfterNow(jobs, "master-review");
        E2eWorkspace.awaitAFullRunStartedAfterNow(jobs, "master-answer");
        return task();
    }

    private TaskState awaitTask(Predicate<TaskState> reached) {
        return await().atMost(A_FEW_TICKS_AND_A_DEPLOY_TICK).pollInterval(Duration.ofMillis(500))
                .until(this::task, reached::test);
    }

    private void awaitTold(String line) {
        Path context = worktree().resolve("task_context.md");
        await().atMost(A_FEW_TICKS_AND_A_DEPLOY_TICK).pollInterval(Duration.ofMillis(500))
                .until(() -> Files.readString(context).contains(line));
    }

    private void agentReports(String status, String message, String extraArgument) throws Exception {
        String call = """
                {"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                 "params": {"name": "update_agent_status",
                            "arguments": {"status": "%s", "message": "%s"%s}}}"""
                .formatted(status, message, extraArgument);
        assertThat(post("/mcp", call, Map.of("X-Working-Directory", worktree().toString())))
                .contains("-> " + status);
    }

    private String act(String action) throws Exception {
        return post("/api/tasks/actions/" + action + "?task=" + TASK, "", Map.of());
    }

    private String post(String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        HttpResponse<String> answer = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(answer.statusCode()).as("%s", answer.body()).isEqualTo(200);
        return answer.body();
    }

    private String request() {
        return E2eWorkspace.requestUrl(workspace.resolve("origin.git"));
    }

    private TaskState task() {
        return stateService.task(TASK).orElseThrow();
    }

    private Path repo() {
        return workspace.resolve("proj");
    }

    private Path worktree() {
        return workspace.resolve(TASK + "-proj");
    }
}

package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.assistant.MasterAssistant;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.ActionOrigin;
import dev.jagt.orchestrator.model.MergeRequestFacts;
import dev.jagt.orchestrator.model.MergeRequestSpec;
import dev.jagt.orchestrator.model.NewTask;
import dev.jagt.orchestrator.model.ReviewFacts;
import dev.jagt.orchestrator.model.StatusChange;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.EditorDriver;
import dev.jagt.orchestrator.platform.TerminalDriver;
import dev.jagt.orchestrator.platform.UserNotifier;
import dev.jagt.orchestrator.service.GitService;
import dev.jagt.orchestrator.service.IdeRecentProjectsCleaner;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.shell.MasterShell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * What happens to a task AFTER an agent is done with it: ship, the review rounds it comes back for, deploy and
 * the undo — over real git, with the code host faked and no model anywhere. The verbs are driven the way the
 * board drives them, so what is asserted is what a human is actually told; the agent reports the way a real one
 * does, over {@code POST /mcp} with its worktree in the header, which is what makes its status carry an origin
 * of its own.
 *
 * <p>Not part of {@code ./gradlew test}. It complements the lifecycle matrix in {@code TaskFlowMatrixTest}:
 * that one covers creation and teardown across the viewer combinations, this one everything in between on a
 * single combination — a review round does not vary with how terminals are arranged.
 *
 * <p>The conflicted deploy is covered here too — the half-state sentence and the finish-after-resolve push —
 * because only a real merge produces them. Which git exit code means a conflict AT ALL stays in
 * {@code GitServiceTest}, which needs no task, no worktree and no host to assert it.
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"orchestrator.agent=stub", "orchestrator.open-warp-window=false"})
class ReviewAndDeployFlowTest {

    private static final String TASK = "ABC-1";
    private static final String TITLE = "Widget layout is off";

    private final HttpClient client = HttpClient.newHttpClient();

    @TempDir
    static Path workspace;

    @DynamicPropertySource
    static void orchestratorLivesInTheTempWorkspace(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> workspace.resolve("root").toString());
        registry.add("orchestrator.config-file", () -> workspace.resolve("root/config.json").toString());
        registry.add("orchestrator.state-file", () -> workspace.resolve("root/state.json").toString());
    }

    @TestConfiguration
    static class TheOnlyHostThisRunHas {

        @Bean
        FakeCodeHost fakeCodeHost() {
            return new FakeCodeHost(E2eWorkspace.requestUrl(workspace.resolve("origin.git")));
        }
    }

    @MockitoBean
    private MasterShell masterShell;
    @MockitoBean
    private IdeRecentProjectsCleaner ideRecentProjectsCleaner;
    @MockitoBean
    private TerminalDriver terminalDriver;
    @MockitoBean
    private EditorDriver editorDriver;
    @MockitoBean
    private UserNotifier userNotifier;
    /** Doubled so that a flow which stopped routing its reads through the host fails instead of paying. */
    @MockitoBean
    private MasterAssistant assistant;

    @Autowired
    private FakeCodeHost host;
    @Autowired
    private TaskProvisioning provisioning;
    @Autowired
    private StateService stateService;
    @Autowired
    private OrchestratorPaths paths;
    @Autowired
    private OrchestratorProperties properties;
    @LocalServerPort
    private int port;

    @BeforeAll
    static void createTheThrowawayOutsideWorld() throws Exception {
        E2eWorkspace.createRootMarker(workspace.resolve("root"));
        E2eWorkspace.createRepositoryWithOrigin(workspace.resolve("origin.git"), workspace.resolve("proj"));
        E2eWorkspace.createRepositoryWithOrigin(workspace.resolve("web-origin.git"), workspace.resolve("web"));
    }

    @BeforeEach
    void oneProjectAndAHostThatRemembersNothing() throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), repo(), "shared", false);
        host.forgetEverything();
    }

    @AfterEach
    void leaveNothingBehindForTheNextFlow() {
        stateService.removeTask(TASK);
        E2eWorkspace.forgetTask(repo(), worktree(), TASK);
        E2eWorkspace.forgetTask(webRepo(), webWorktree(), TASK);
        // The deploy branch too: a flow that landed (or conflicted on) a change leaves it there, and the next
        // flow's own deploy would then be merging into someone else's history.
        E2eWorkspace.resetDeployBranch(repo());
        E2eWorkspace.resetDeployBranch(webRepo());
        E2eWorkspace.killTmuxSessions(properties.tmuxCommand());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("dev.jagt.orchestrator.e2e.ReviewRoundCase#matrix")
    void aSweptRoundEndsWhereItsFactsSayAndBriefsTheAgentOnlyWhenThereIsWork(ReviewRoundCase round)
            throws Exception {
        shipTheFirstRound();
        host.answers(round.round());

        assertThat(act("sweep")).contains(round.sentence());

        assertThat(task().status()).isEqualTo(round.expected());
        assertThat(Files.readString(worktree().resolve("task_context.md")).contains("Review round for"))
                .as("the agent was handed this round").isEqualTo(round.briefed());
    }

    @Test
    void aRoundTripFromShipToDeployAndBackOutAgain() throws Exception {
        String shipped = shipTheFirstRound();
        assertThat(shipped).contains("file(s), pushed, opened " + host.requestUrl(), "CI_POLLING");
        assertThat(host.writes()).singleElement()
                .extracting(MergeRequestSpec::sourceBranch, MergeRequestSpec::targetBranch,
                        MergeRequestSpec::title)
                .containsExactly(TASK, "main", TASK + " " + TITLE);
        assertThat(task().mrUrl()).isEqualTo(host.requestUrl());
        assertThat(E2eWorkspace.git(origin(), "log", "-1", "--format=%s", TASK))
                .contains(TASK + " " + TITLE);

        host.answers(new ReviewFacts(true, false, "success", List.of("bot (Widget.java:12): tighten this")));
        act("sweep");
        assertThat(Files.readString(worktree().resolve("task_context.md")))
                .contains("bot (Widget.java:12): tighten this", "Do NOT push or post anything yourself");

        Files.writeString(worktree().resolve("widget.txt"), "tightened\n");
        Files.writeString(worktree().resolve("review_replies.md"), "> tighten this\n\nDone.\n");
        agentReports("REVIEW_PENDING", "comment addressed");

        assertThat(act("ship")).contains("updated " + host.requestUrl(),
                "drafted replies relayed");
        assertThat(host.writes()).hasSize(2);
        assertThat(E2eWorkspace.git(origin(), "log", "-1", "--format=%s", TASK))
                .contains(TASK + " address review comments");
        assertThat(Files.readString(worktree().resolve("task_context.md")))
                .contains("there is NOTHING to commit or push");

        host.answers(new ReviewFacts(true, true, "success", List.of()));
        assertThat(act("sweep")).contains("approved, checks success");
        assertThat(task().status()).isEqualTo(TaskStatus.APPROVED);

        assertThat(act("deploy")).contains("Merged " + TASK + " into dev", "DEPLOYED");
        assertThat(task().deployCommit()).isNotBlank();
        assertThat(E2eWorkspace.git(origin(), "log", "-1", "--format=%s", "dev"))
                .contains("Merge branch '" + TASK + "' into dev");

        assertThat(act("revert")).contains("Reverted " + TASK + " on dev", "REVERTED");
        assertThat(E2eWorkspace.git(origin(), "log", "-1", "--format=%s", "dev"))
                .contains("Revert \"Merge branch '" + TASK + "' into dev\"");

        assertThat(task().history()).extracting(StatusChange::status)
                .containsExactly(TaskStatus.NEW, TaskStatus.REVIEW_PENDING, TaskStatus.CI_POLLING,
                        TaskStatus.REVIEW_PENDING, TaskStatus.CI_POLLING, TaskStatus.APPROVED,
                        TaskStatus.DEPLOYED, TaskStatus.REVERTED);
        assertThat(task().history()).extracting(StatusChange::status, StatusChange::origin)
                .contains(tuple(TaskStatus.REVIEW_PENDING, ActionOrigin.MCP),
                        tuple(TaskStatus.DEPLOYED, ActionOrigin.BOARD));

        assertThat(act("done")).contains("worktree deleted", "Branch '" + TASK + "' was kept");
        assertThat(worktree()).doesNotExist();
        assertThat(stateService.task(TASK)).isEmpty();
        assertThat(E2eWorkspace.git(repo(), "branch", "--list", TASK)).contains(TASK);
    }

    /**
     * The half-state, which only a real merge can produce: the first repository is pushed to the shared branch
     * and the second conflicts, so the sentence has to name BOTH sides and a second `deploy` must finish the one
     * that is waiting without touching the one already live.
     */
    @Test
    void stopsAtTheRepositoryThatConflictsAndFinishesItOnTheNextDeploy() throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), new LinkedHashMap<>(Map.of(
                "proj", repo(), "web", webRepo())), "shared", false);

        provisioning.initializeTask(NewTask.builder(TASK, "proj").alsoIn(List.of("web"))
                .instructions("Move both sides of the contract").title(TITLE).build());
        Files.writeString(worktree().resolve("widget.txt"), "api side\n");
        Files.writeString(webWorktree().resolve("widget.txt"), "web side\n");
        agentReports("REVIEW_PENDING", "both sides done");
        act("ship");
        host.answers(new ReviewFacts(true, true, "success", List.of()));
        act("sweep");
        E2eWorkspace.commitOnDeployBranch(webRepo(), "widget.txt", "someone else's line\n");

        String conflicted = act("deploy");

        assertThat(conflicted).contains("CONFLICT merging web into dev",
                "Live on the deploy branch: proj", "NOT deployed: web");
        assertThat(task().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
        assertThat(E2eWorkspace.git(origin(), "log", "-1", "--format=%s", "dev"))
                .contains("Merge branch '" + TASK + "' into dev");
        assertThat(E2eWorkspace.git(webOrigin(), "log", "-1", "--format=%s", "dev"))
                .doesNotContain("Merge branch '" + TASK + "' into dev");

        Path resolveIn = GitService.deployWorktreePath(webRepo(), TASK);
        Files.writeString(resolveIn.resolve("widget.txt"), "both sides, resolved\n");
        E2eWorkspace.git(resolveIn, "add", "widget.txt");

        assertThat(act("deploy")).contains("DEPLOYED");
        assertThat(E2eWorkspace.git(webOrigin(), "log", "-1", "--format=%s", "dev"))
                .contains("Merge branch '" + TASK + "' into dev");
        assertThat(task().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void oneSessionShipsAndDeploysEveryRepositoryTheTaskSpansAndTakesThemBackOutInReverse() throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), new LinkedHashMap<>(Map.of(
                "proj", repo(), "web", webRepo())), "shared", false);

        provisioning.initializeTask(NewTask.builder(TASK, "proj").alsoIn(List.of("web"))
                .instructions("Move both sides of the contract").title(TITLE).build());
        Files.writeString(worktree().resolve("widget.txt"), "api side\n");
        Files.writeString(webWorktree().resolve("widget.txt"), "web side\n");
        agentReports("REVIEW_PENDING", "both sides done");

        String shipped = act("ship");

        assertThat(shipped).contains("proj 1 file(s), pushed", "web 1 file(s), pushed");
        assertThat(host.writes()).extracting(MergeRequestSpec::remoteUrl)
                .containsExactly(E2eWorkspace.remoteUrl(origin()), E2eWorkspace.remoteUrl(webOrigin()));
        assertThat(E2eWorkspace.git(webOrigin(), "log", "-1", "--format=%s", TASK))
                .contains(TASK + " " + TITLE);
        assertThat(task().history()).extracting(StatusChange::status)
                .containsExactly(TaskStatus.NEW, TaskStatus.REVIEW_PENDING, TaskStatus.CI_POLLING);

        host.answers(new ReviewFacts(true, true, "success", List.of()));
        act("sweep");
        assertThat(task().status()).isEqualTo(TaskStatus.APPROVED);

        assertThat(act("deploy")).contains("proj into dev", "web into dev", "DEPLOYED");
        assertThat(E2eWorkspace.git(origin(), "log", "-1", "--format=%s", "dev"))
                .contains("Merge branch '" + TASK + "' into dev");
        assertThat(E2eWorkspace.git(webOrigin(), "log", "-1", "--format=%s", "dev"))
                .contains("Merge branch '" + TASK + "' into dev");

        assertThat(act("revert")).contains("reverted web on dev", "proj on dev", "REVERTED");
        assertThat(E2eWorkspace.git(webOrigin(), "log", "-1", "--format=%s", "dev"))
                .contains("Revert \"Merge branch '" + TASK + "' into dev\"");
    }

    @Test
    void resumeAdoptsAnOpenRequestOnTheBranchThatIsAlreadyThere() throws Exception {
        E2eWorkspace.git(repo(), "branch", TASK, "main");
        host.answers(new MergeRequestFacts(true, TASK, "main", TASK + " " + TITLE));

        String resumed = post("/api/tasks/resume",
                "{\"reviewRequestUrl\": \"" + host.requestUrl() + "\"}", Map.of());

        assertThat(resumed).contains("Resumed " + TASK + " on its existing branch", "CI_POLLING");
        assertThat(task().status()).isEqualTo(TaskStatus.CI_POLLING);
        assertThat(task().mrUrl()).isEqualTo(host.requestUrl());
        assertThat(task().baseBranch()).isEqualTo("main");
        assertThat(task().title()).isEqualTo(TITLE);
        assertThat(worktree().resolve("task_context.md")).exists();
        verifyNoInteractions(assistant);
    }

    private String shipTheFirstRound() throws Exception {
        provisioning.initializeTask(NewTask.builder(TASK, "proj")
                .instructions("Fix the widget").title(TITLE).build());
        Files.writeString(worktree().resolve("widget.txt"), "fixed\n");
        agentReports("REVIEW_PENDING", "widget fixed");
        return act("ship");
    }

    private void agentReports(String status, String message) throws Exception {
        String call = """
                {"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                 "params": {"name": "update_agent_status",
                            "arguments": {"status": "%s", "message": "%s"}}}"""
                .formatted(status, message);
        assertThat(post("/mcp", call, Map.of("X-Working-Directory", worktree().toString())))
                .contains("-> " + status);
    }

    private String act(String action) throws Exception {
        return post("/api/tasks/" + TASK + "/actions/" + action, "", Map.of());
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

    private TaskState task() {
        return stateService.task(TASK).orElseThrow();
    }

    private Path repo() {
        return workspace.resolve("proj");
    }

    private Path origin() {
        return workspace.resolve("origin.git");
    }

    private Path webRepo() {
        return workspace.resolve("web");
    }

    private Path webOrigin() {
        return workspace.resolve("web-origin.git");
    }

    private Path webWorktree() {
        return workspace.resolve(TASK + "-web");
    }

    private Path worktree() {
        return workspace.resolve(TASK + "-proj");
    }
}

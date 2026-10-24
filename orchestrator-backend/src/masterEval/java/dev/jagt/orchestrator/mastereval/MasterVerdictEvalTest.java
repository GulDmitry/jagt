package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.eval.EvalPorts;
import dev.jagt.orchestrator.eval.EvalQuorum;
import dev.jagt.orchestrator.eval.MasterVerdictReport;
import dev.jagt.orchestrator.surface.agent.ReadGateController;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.task.TaskState;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("masterEval")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {"spring.config.import=",
        "orchestrator.startup-checks=false", "orchestrator.open-terminal-window=false"})
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class MasterVerdictEvalTest {

    @MockitoBean
    private UserNotifier userNotifier;

    private static final int DEAD_PORT = EvalPorts.free();
    private static final int GATE_PORT = EvalPorts.free();

    private static final Duration VERDICT_WAIT = Duration.ofMinutes(20);

    private static final AtomicInteger ROUND = new AtomicInteger();

    @DynamicPropertySource
    static void orchestratorLivesInTheTrustedEvalRoot(DynamicPropertyRegistry registry) throws Exception {
        registry.add("server.port", () -> GATE_PORT);
        registry.add("orchestrator.mcp-url", () -> "http://127.0.0.1:" + DEAD_PORT + "/mcp");
        registry.add("orchestrator.hook-url", () -> "http://127.0.0.1:" + DEAD_PORT + "/api/agent/session");
        registry.add("orchestrator.gate-url", () -> "http://127.0.0.1:" + GATE_PORT + "/api/agent");
        MasterEvalWorkspace.claim();
        MasterEvalWorkspace.clean();
        Path root = MasterEvalWorkspace.root();
        Files.createDirectories(root);
        Files.writeString(root.resolve("mcp_client.js"), "// master eval placeholder proxy\n");
        try (var shipped = Files.newDirectoryStream(Path.of(".."), "*.dist")) {
            for (Path file : shipped) {
                Files.copy(file, root.resolve(file.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.copy(Path.of(System.getProperty("masterEval.brief", "../master-brief.md.dist")),
                root.resolve("master-brief.md"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        MasterEvalWorkspace.writeConfig(root.resolve("jagt.yml"), root.resolve("placeholder"),
                "master-brief.md");
        registry.add("orchestrator.root", () -> root.toString());
        registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
    }

    @Autowired
    private StateService stateService;
    @Autowired
    private MasterReview reviews;
    @Autowired
    private UsageTracker usage;
    @MockitoSpyBean
    private ReadGateController readGate;

    @BeforeAll
    static void nothingLiveIsWithinReach() throws Exception {
        refuseIfSomethingAnswers(DEAD_PORT);
    }

    @AfterAll
    void turnsOffTheJobStillTickingInTheUnclosedContext() throws Exception {
        TokenUsage spent = usage.sessionByKind().getOrDefault(AssistantCallKind.MASTER_REVIEW, TokenUsage.NONE);
        LoggerFactory.getLogger(MasterVerdictEvalTest.class).atInfo().setMessage("master eval spent")
                .addKeyValue("calls", spent.calls())
                .addKeyValue("costUsd", spent.costUsd())
                .addKeyValue("inputTokens", spent.inputTokens())
                .addKeyValue("cachedInputTokens", spent.cachedInputTokens())
                .addKeyValue("outputTokens", spent.outputTokens())
                .log();
        try {
            MasterEvalWorkspace.writeConfig(MasterEvalWorkspace.root().resolve("jagt.yml"),
                    MasterEvalWorkspace.root().resolve("placeholder"), "master-brief.md", "off");
        } finally {
            MasterEvalWorkspace.release();
        }
    }

    private static void refuseIfSomethingAnswers(int port) {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            throw new IllegalStateException("Something is listening on 127.0.0.1:" + port
                    + " — masterEval points its reviews there precisely because nothing should be. Move it.");
        } catch (java.io.IOException nothingThereAsRequired) {
            return;
        }
    }

    @ParameterizedTest
    @MethodSource("rounds")
    void handsBackTheVerdictAHumanWouldHave(MasterCase round) {
        EvalQuorum.configured().hold(round.instructions(), () -> oneRun(round));
    }

    private void oneRun(MasterCase round) throws Exception {
        Mockito.clearInvocations(readGate);
        String taskId = "ABC-" + ROUND.incrementAndGet();
        Path worktree = MasterEvalWorkspace.worktreeFor(
                MasterEvalWorkspace.root().resolve("repos/" + taskId + "/repo"), round, taskId);
        TaskState task = TaskState.builder("proj", worktree.toString(), round.status())
                .alias("m" + ROUND.get()).title(round.instructions()).build();
        stateService.putTask(taskId, task);
        Optional<MasterReview.Verdict> verdict;
        try {
            verdict = awaitVerdict(task);
        } finally {
            stateService.removeTask(taskId);
        }

        assertThat(verdict).describedAs("no verdict inside %s", VERDICT_WAIT).isPresent();
        String report = MasterVerdictReport.of(round.verdict() + ", naming " + round.names(), worktree);
        assertThat(verdict.orElseThrow().kind()).describedAs("%s", report).isEqualTo(round.verdict());
        MasterReview.Verdict judged = verdict.orElseThrow();
        String named = String.join("\n", Stream.concat(Stream.concat(judged.findings().stream(),
                judged.advice().stream()).map(MasterReview.Said::line), judged.questions().stream()).toList());
        assertThat(round.names()).describedAs("%s", report)
                .allSatisfy(name -> assertThat(named).containsAnyOf(name.split("\\|")));
        List<ReadGateController.ToolCall> heard = Mockito.mockingDetails(readGate).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("gate"))
                .map(call -> (ReadGateController.ToolCall) call.getArgument(1)).toList();
        assertThat(heard).describedAs("calls heard: %s", heard).anyMatch(call -> call.cwd() != null
                && Path.of(call.cwd()).normalize().startsWith(worktree.normalize()));
    }

    private Optional<MasterReview.Verdict> awaitVerdict(TaskState task) {
        try {
            return await().atMost(VERDICT_WAIT).pollInterval(Duration.ofSeconds(2))
                    .until(() -> reviews.of(task), Optional::isPresent);
        } catch (ConditionTimeoutException noVerdict) {
            return Optional.empty();
        }
    }

    static List<MasterCase> rounds() {
        return MasterCase.matrix();
    }
}

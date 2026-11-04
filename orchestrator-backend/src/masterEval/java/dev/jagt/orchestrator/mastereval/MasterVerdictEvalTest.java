package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.eval.EvalPorts;
import dev.jagt.orchestrator.eval.EvalQuorum;
import dev.jagt.orchestrator.eval.EvalStalled;
import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.eval.MasterVerdictReport;
import dev.jagt.orchestrator.surface.agent.ReadGateController;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.adapter.agent.MasterEvalTranscripts;
import dev.jagt.orchestrator.task.AgentReport;
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
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("masterEval")
@org.junit.jupiter.api.extension.ExtendWith(MasterVerdictEvalTest.ClaimsItsShard.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {"spring.config.import=",
        "orchestrator.startup-checks=false", "orchestrator.open-terminal-window=false"})
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
abstract class MasterVerdictEvalTest {

    @MockitoBean
    private UserNotifier userNotifier;

    private static final int DEAD_PORT = EvalPorts.free();
    private static final int GATE_PORT = EvalPorts.free();

    private static final Duration VERDICT_WAIT = Duration.ofMinutes(20);

    private static final String DECIDED = " \u2014 decided: ";

    private static final AtomicInteger ROUND = new AtomicInteger();

    static final class ClaimsItsShard implements org.junit.jupiter.api.extension.TestInstancePostProcessor {
        @Override
        public void postProcessTestInstance(Object instance, org.junit.jupiter.api.extension.ExtensionContext context)
                throws Exception {
            MasterEvalWorkspace.claim(context.getRequiredTestClass());
        }
    }

    @DynamicPropertySource
    static void orchestratorLivesInTheTrustedEvalRoot(DynamicPropertyRegistry registry) throws Exception {
        registry.add("server.port", () -> GATE_PORT);
        registry.add("orchestrator.mcp-url", () -> "http://127.0.0.1:" + DEAD_PORT + "/mcp");
        registry.add("orchestrator.hook-url", () -> "http://127.0.0.1:" + DEAD_PORT + "/api/agent/session");
        registry.add("orchestrator.gate-url", () -> "http://127.0.0.1:" + GATE_PORT + "/api/agent");
        MasterEvalWorkspace.clean();
        Path root = MasterEvalWorkspace.root();
        Files.createDirectories(root);
        Files.writeString(root.resolve("mcp_client.js"), "// master eval placeholder proxy\n");
        ShippedRoot.copyInto(root);
        Files.createDirectories(root.resolve("roles/master"));
        Files.copy(Path.of(System.getProperty("masterEval.brief", "../roles/master/brief.md.dist")),
                root.resolve("roles/master/brief.md"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        MasterEvalWorkspace.writeConfig(root.resolve("jagt.yml"), root.resolve("placeholder"),
                "roles/master/brief.md");
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
    @Autowired
    private MasterDecisions decisions;
    @MockitoSpyBean
    private ReadGateController readGate;
    @MockitoSpyBean
    private MasterPanel panel;

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
                    MasterEvalWorkspace.root().resolve("placeholder"), "roles/master/brief.md", "off");
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
        EvalQuorum.configured().hold(round.instructions(), this::masterReviewSpend, () -> oneRun(round));
    }

    private OptionalDouble masterReviewSpend() {
        return OptionalDouble.of(usage.sessionByKind().getOrDefault(AssistantCallKind.MASTER_REVIEW, TokenUsage.NONE).costUsd());
    }

    private void oneRun(MasterCase round) throws Exception {
        Mockito.clearInvocations(readGate);
        String taskId = "ABC-" + ROUND.incrementAndGet();
        Path worktree = MasterEvalWorkspace.worktreeFor(
                MasterEvalWorkspace.root().resolve("repos/" + taskId + "/repo"), round, taskId);
        TaskState task = TaskState.builder("proj", worktree.toString(), round.status())
                .alias("m" + ROUND.get()).title(round.instructions()).build();
        if (round.answers()) {
            decidesAsAHumanWould(round, taskId, task.withReport(AgentReport.QUESTION).withStatus(
                    round.status(), round.answer().question(), 0), worktree);
        } else {
            handsBackAVerdict(round, taskId, task, worktree);
        }
        List<ReadGateController.ToolCall> heard = Mockito.mockingDetails(readGate).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("gate"))
                .map(call -> (ReadGateController.ToolCall) call.getArgument(1)).toList();
        assertThat(heard).describedAs("calls heard: %s", heard).anyMatch(call -> call.cwd() != null
                && Path.of(call.cwd()).normalize().startsWith(worktree.normalize()));
    }

    private void handsBackAVerdict(MasterCase round, String taskId, TaskState task, Path worktree) {
        stateService.putTask(taskId, task);
        Optional<MasterReview.Verdict> verdict;
        try {
            verdict = awaitVerdict(taskId);
        } finally {
            stateService.removeTask(taskId);
        }

        if (verdict.isEmpty()) {
            throw EvalStalled.because("no verdict inside " + VERDICT_WAIT);
        }
        String report = MasterVerdictReport.of(round.verdict() + ", naming " + round.names(), worktree);
        assertThat(verdict.orElseThrow().kind()).describedAs("%s", report).isEqualTo(round.verdict());
        MasterReview.Verdict judged = verdict.orElseThrow();
        String named = String.join("\n", Stream.concat(Stream.concat(judged.findings().stream(),
                judged.advice().stream()).map(MasterReview.Said::line), judged.questions().stream()).toList());
        assertThat(round.names()).describedAs("%s", report)
                .allSatisfy(name -> assertThat(named).containsAnyOf(name.split("\\|")));
    }

    private void decidesAsAHumanWould(MasterCase round, String taskId, TaskState task, Path worktree)
            throws Exception {
        MasterEvalTranscripts.typedIn(worktree, round.answer().said());
        AtomicReference<String> firstLine = new AtomicReference<>();
        Mockito.doAnswer(call -> {
            Object made = call.callRealMethod();
            ((Optional<?>) made).map(decision -> ((MasterPanel.Decision) decision).text())
                    .ifPresent(text -> firstLine.set(text.lines().findFirst().orElse("")));
            return made;
        }).when(panel).answer(Mockito.eq(taskId), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean());
        masterMode("act");
        Optional<String> decided;
        try {
            stateService.putTask(taskId, task);
            decided = awaitDecision(task).map(recorded -> firstLine.get());
        } finally {
            stateService.removeTask(taskId);
            masterMode("judge");
        }

        if (decided.isEmpty()) {
            throw EvalStalled.because("no decision inside " + VERDICT_WAIT);
        }
        String report = MasterVerdictReport.of("a decision whose first line names " + round.names() + ", never "
                + round.answer().refused(), worktree);
        String text = decided.orElseThrow().toLowerCase(Locale.ROOT);
        assertThat(round.names()).describedAs("%s", report)
                .allSatisfy(name -> assertThat(text).containsAnyOf(alternatives(name)));
        assertThat(round.answer().refused()).describedAs("%s", report)
                .allSatisfy(name -> assertThat(text).doesNotContain(alternatives(name)));
    }

    private static String[] alternatives(String names) {
        return names.toLowerCase(Locale.ROOT).split("\\|");
    }

    private static void masterMode(String mode) throws Exception {
        MasterEvalWorkspace.writeConfig(MasterEvalWorkspace.root().resolve("jagt.yml"),
                MasterEvalWorkspace.root().resolve("placeholder"), "roles/master/brief.md", mode);
    }

    private Optional<String> awaitDecision(TaskState task) {
        try {
            return await().atMost(VERDICT_WAIT).pollInterval(Duration.ofSeconds(2))
                    .until(() -> decisions.of(task), recorded -> recorded.contains(DECIDED))
                    .lines().filter(line -> line.contains(DECIDED))
                    .map(line -> line.substring(line.indexOf(DECIDED) + DECIDED.length())).findFirst();
        } catch (ConditionTimeoutException noDecision) {
            return Optional.empty();
        }
    }

    private Optional<MasterReview.Verdict> awaitVerdict(String taskId) {
        try {
            return await().atMost(VERDICT_WAIT).pollInterval(Duration.ofSeconds(2))
                    .until(() -> stateService.task(taskId).flatMap(reviews::of), Optional::isPresent);
        } catch (ConditionTimeoutException noVerdict) {
            return Optional.empty();
        }
    }

    static List<MasterCase> rounds() {
        List<MasterCase> matrix = MasterCase.matrix();
        return IntStream.range(0, matrix.size())
                .filter(index -> index % MasterEvalWorkspace.shards() == MasterEvalWorkspace.shard())
                .mapToObj(matrix::get).toList();
    }
}

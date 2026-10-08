package dev.jagt.orchestrator.mastereval;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("masterEval")
@SpringBootTest(properties = {"spring.config.import=", "orchestrator.startup-checks=false",
        "orchestrator.open-terminal-window=false",
        "orchestrator.mcp-url=http://127.0.0.1:" + MasterVerdictEvalTest.DEAD_PORT + "/mcp",
        "orchestrator.hook-url=http://127.0.0.1:" + MasterVerdictEvalTest.DEAD_PORT + "/api/agent/session",
        "orchestrator.gate-url=http://127.0.0.1:" + MasterVerdictEvalTest.DEAD_PORT + "/api/agent"})
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class MasterVerdictEvalTest {

    static final String DEAD_PORT = "8391";

    private static final Duration VERDICT_WAIT = Duration.ofMinutes(20);

    private static final AtomicInteger ROUND = new AtomicInteger();

    @DynamicPropertySource
    static void orchestratorLivesInTheTrustedEvalRoot(DynamicPropertyRegistry registry) throws Exception {
        MasterEvalWorkspace.claim();
        MasterEvalWorkspace.clean();
        Path root = MasterEvalWorkspace.root();
        Files.createDirectories(root);
        Files.writeString(root.resolve("mcp_client.js"), "// master eval placeholder proxy\n");
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

    @BeforeAll
    static void nothingLiveIsWithinReach() throws Exception {
        refuseIfSomethingAnswers(Integer.parseInt(DEAD_PORT));
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
    void handsBackTheVerdictAHumanWouldHave(MasterCase round) throws Exception {
        String taskId = "ABC-" + ROUND.incrementAndGet();
        Path worktree = MasterEvalWorkspace.worktreeFor(
                MasterEvalWorkspace.root().resolve("repos/" + taskId + "/repo"), round, taskId);
        TaskState task = TaskState.builder("proj", worktree.toString(), round.status())
                .alias("m" + ROUND.get()).title(round.instructions()).build();
        stateService.putTask(taskId, task);
        Optional<MasterReview.Verdict> verdict = awaitVerdict(task);
        stateService.removeTask(taskId);

        assertThat(verdict).describedAs("no verdict inside %s", VERDICT_WAIT).isPresent();
        assertThat(verdict.orElseThrow().kind()).describedAs("%s", verdict.orElseThrow().findings())
                .isEqualTo(round.verdict());
        String review = Files.readString(reviews.file(task));
        assertThat(round.names()).allSatisfy(name -> assertThat(review).contains(name));
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

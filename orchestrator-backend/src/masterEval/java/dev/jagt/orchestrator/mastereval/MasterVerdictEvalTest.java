package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.service.MasterReview;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.task.TaskState;
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

/**
 * The reviewer that replaces the first reading of every diff, read against rounds whose answer is already known.
 * It judges by the SHIPPED brief rather than an install's own, so what this measures is the document, and the
 * whole path is the real one: the job's trigger, the panel, the file it writes and how that file is parsed.
 */
@Tag("masterEval")
@SpringBootTest(properties = {"spring.config.import=", "orchestrator.startup-checks=false",
        "orchestrator.open-terminal-window=false",
        // Every one of these defaults to the running board's own port. A review started here is a real CLI
        // with real tools, and pointed at a live install it would act on tasks that are not the suite's.
        "orchestrator.mcp-url=http://127.0.0.1:" + MasterVerdictEvalTest.DEAD_PORT + "/mcp",
        "orchestrator.hook-url=http://127.0.0.1:" + MasterVerdictEvalTest.DEAD_PORT + "/api/agent/session",
        "orchestrator.gate-url=http://127.0.0.1:" + MasterVerdictEvalTest.DEAD_PORT + "/api/agent"})
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class MasterVerdictEvalTest {

    /** Nothing may listen here, and the suite refuses to start if anything does. */
    static final String DEAD_PORT = "8391";

    /** Every role of the panel reading the round, run in parallel, plus the job's next tick. */
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

    /** The job is still ticking in a context nobody has closed; turned OFF in the file it re-reads. */
    @AfterAll
    void leavesNothingOfItsOwnRunning() throws Exception {
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

    /**
     * The one failure this suite must not have: a review of its own reaching an install that is not it. The
     * port it is pointed at is proved dead before a review exists to use it.
     */
    private static void refuseIfSomethingAnswers(int port) {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            throw new IllegalStateException("Something is listening on 127.0.0.1:" + port
                    + " — masterEval points its reviews there precisely because nothing should be. Move it.");
        } catch (java.io.IOException refused) {
            // Nothing there, which is the whole requirement.
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
        // The verdict line carries one word; what the review is ABOUT is the file it wrote. A round with
        // nothing to find names nothing, and that is an empty list rather than a case of its own.
        String review = Files.readString(reviews.file(task));
        assertThat(round.names()).allSatisfy(name -> assertThat(review).contains(name));
    }

    /** The job is what asks; this only waits for the file it ends up writing. */
    private Optional<MasterReview.Verdict> awaitVerdict(TaskState task) throws InterruptedException {
        long deadline = System.nanoTime() + VERDICT_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<MasterReview.Verdict> written = reviews.of(task);
            if (written.isPresent()) {
                return written;
            }
            Thread.sleep(2_000);
        }
        return Optional.empty();
    }

    static List<MasterCase> rounds() {
        return MasterCase.matrix();
    }
}

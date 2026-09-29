package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.service.MasterReview;
import dev.jagt.orchestrator.service.MasterSession;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
 * whole path is the real one: the job's trigger, the session, the file it writes and how that file is parsed.
 */
@Tag("masterEval")
@SpringBootTest(properties = {"spring.config.import=", "orchestrator.startup-checks=false",
        "orchestrator.open-terminal-window=false",
        // Every one of these defaults to the running board's own port. A session started here is a real CLI
        // with real tools, and pointed at a live install it would act on tasks that are not the suite's.
        "orchestrator.mcp-url=http://127.0.0.1:" + MasterVerdictEvalTest.DEAD_PORT + "/mcp",
        "orchestrator.hook-url=http://127.0.0.1:" + MasterVerdictEvalTest.DEAD_PORT + "/api/agent/session",
        "orchestrator.gate-url=http://127.0.0.1:" + MasterVerdictEvalTest.DEAD_PORT + "/api/agent"})
class MasterVerdictEvalTest {

    /** Nothing may listen here, and the suite refuses to start if anything does. */
    static final String DEAD_PORT = "8391";

    /** One turn of a heavy model reading a diff, plus the wait for it to be typed at. */
    private static final Duration VERDICT_WAIT = Duration.ofMinutes(6);

    private static final AtomicInteger ROUND = new AtomicInteger();

    @DynamicPropertySource
    static void orchestratorLivesInTheTrustedEvalRoot(DynamicPropertyRegistry registry) throws Exception {
        MasterEvalWorkspace.clean();
        Path root = MasterEvalWorkspace.root();
        Files.createDirectories(root);
        Files.writeString(root.resolve("mcp_client.js"), "// master eval placeholder proxy\n");
        Files.copy(Path.of("..", "master-brief.md.dist"), root.resolve("master-brief.md"),
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
    private MasterSession master;
    @Autowired
    private MasterReview reviews;

    @BeforeAll
    static void nothingLiveIsWithinReachAndTheRootIsOneAHumanTrusted() throws Exception {
        refuseIfSomethingAnswers(Integer.parseInt(DEAD_PORT));
        refuseUntilAHumanHasTrustedTheRoot();
        killTmux();
    }

    /**
     * An agent CLI started where nobody said it may work sits at its own prompt forever, and the suite would
     * read that as a reviewer with nothing to say. Trust is a human's to give, so this only checks.
     */
    private static void refuseUntilAHumanHasTrustedTheRoot() throws Exception {
        Path root = MasterEvalWorkspace.root();
        if (MasterEvalWorkspace.trusted(root)) {
            return;
        }
        throw new IllegalStateException("The eval root is not one this machine trusts, so no session would"
                + " start in it. Run `claude` once in " + root + " and accept, then run this again.");
    }

    /**
     * The one failure this suite must not have: a session of its own reaching an install that is not it. The
     * port it is pointed at is proved dead before a session exists to use it.
     */
    private static void refuseIfSomethingAnswers(int port) {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            throw new IllegalStateException("Something is listening on 127.0.0.1:" + port
                    + " — masterEval points its session there precisely because nothing should be. Move it.");
        } catch (java.io.IOException refused) {
            // Nothing there, which is the whole requirement.
        }
    }

    @AfterAll
    static void theSessionLeavesNoWindowBehind() throws Exception {
        killTmux();
    }

    @ParameterizedTest
    @MethodSource("rounds")
    void handsBackTheVerdictAHumanWouldHave(MasterCase round) throws Exception {
        String taskId = "ABC-" + ROUND.incrementAndGet();
        Path worktree = MasterEvalWorkspace.worktreeFor(
                MasterEvalWorkspace.root().resolve("repos/" + taskId + "/repo"), round, taskId);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("m" + ROUND.get()).title(round.instructions()).build();
        master.startIfWanted();

        stateService.putTask(taskId, task);
        Optional<MasterReview.Verdict> verdict = awaitVerdict(task);
        stateService.removeTask(taskId);

        assertThat(verdict).describedAs("no verdict inside %s", VERDICT_WAIT).isPresent();
        assertThat(verdict.orElseThrow().ready()).describedAs(verdict.orElseThrow().said())
                .isEqualTo(round.ready());
        assertThat(verdict.orElseThrow().said()).contains(round.names());
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

    private static void killTmux() throws Exception {
        new ProcessBuilder("tmux", "kill-session", "-t", MasterEvalWorkspace.TMUX_SESSION)
                .redirectErrorStream(true).start().waitFor();
    }

    static List<MasterCase> rounds() {
        return MasterCase.matrix();
    }
}

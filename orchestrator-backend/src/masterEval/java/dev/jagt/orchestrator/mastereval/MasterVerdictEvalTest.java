package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.service.MasterReview;
import dev.jagt.orchestrator.service.MasterSession;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
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
        "orchestrator.open-terminal-window=false"})
class MasterVerdictEvalTest {

    /** One turn of a heavy model reading a diff, plus the wait for it to be typed at. */
    private static final Duration VERDICT_WAIT = Duration.ofMinutes(6);

    @TempDir
    static Path workspace;

    private static final AtomicInteger ROUND = new AtomicInteger();

    @DynamicPropertySource
    static void orchestratorLivesInTheTempWorkspace(DynamicPropertyRegistry registry) throws Exception {
        Path root = workspace.resolve("root");
        Files.createDirectories(root);
        Files.writeString(root.resolve("mcp_client.js"), "// master eval placeholder proxy\n");
        Files.copy(Path.of("..", "master-brief.md.dist"), root.resolve("master-brief.md"));
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
    static void nothingIsLeftOverFromALastRun() throws Exception {
        killTmux();
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
                workspace.resolve("repos/" + taskId + "/repo"), round, taskId);
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

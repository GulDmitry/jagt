package dev.jagt.orchestrator.qaeval;

import dev.jagt.orchestrator.config.QaBrief;
import dev.jagt.orchestrator.eval.EvalPorts;
import dev.jagt.orchestrator.eval.EvalQuorum;
import dev.jagt.orchestrator.eval.EvalStalled;
import dev.jagt.orchestrator.eval.QaCase;
import dev.jagt.orchestrator.eval.QaCases;
import dev.jagt.orchestrator.eval.QaFixtureEnvironment;
import dev.jagt.orchestrator.eval.QaJudgement;
import dev.jagt.orchestrator.eval.QaScore;
import dev.jagt.orchestrator.eval.QaScoreboard;
import dev.jagt.orchestrator.job.QaPrompts;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaEnvironment;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("qaEval")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {"spring.config.import=",
        "orchestrator.startup-checks=false", "orchestrator.open-terminal-window=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class QaVerdictEvalTest {

    private static final int DEAD_PORT = EvalPorts.free();
    private static final int GATE_PORT = EvalPorts.free();
    private static final AtomicInteger ROUND = new AtomicInteger();
    private static final QaScoreboard SCOREBOARD = QaScoreboard.empty();

    @DynamicPropertySource
    static void orchestratorLivesInTheTrustedEvalRoot(DynamicPropertyRegistry registry) throws Exception {
        registry.add("server.port", () -> GATE_PORT);
        registry.add("orchestrator.mcp-url", () -> "http://127.0.0.1:" + DEAD_PORT + "/mcp");
        registry.add("orchestrator.hook-url", () -> "http://127.0.0.1:" + DEAD_PORT + "/api/agent/session");
        registry.add("orchestrator.gate-url", () -> "http://127.0.0.1:" + GATE_PORT + "/api/agent");
        QaEvalWorkspace.shard();
        QaEvalWorkspace.clean();
        Path root = QaEvalWorkspace.root();
        Files.createDirectories(root);
        Files.writeString(root.resolve("mcp_client.js"), "// qa eval placeholder proxy\n");
        try (var shipped = Files.newDirectoryStream(Path.of(".."), "*.dist")) {
            for (Path file : shipped) {
                Files.copy(file, root.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.copy(Path.of(System.getProperty("qaEval.brief", "../qa-brief.md.dist")), root.resolve(QaBrief.FILE),
                StandardCopyOption.REPLACE_EXISTING);
        QaEvalWorkspace.writeConfig(root.resolve("jagt.yml"), root.resolve("placeholder"));
        registry.add("orchestrator.root", () -> root.toString());
        registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
    }

    @MockitoBean
    private UserNotifier userNotifier;
    @Autowired
    private QaReader reader;
    @Autowired
    private QaPrompts prompts;
    @Autowired
    private UsageTracker usage;

    @AfterAll
    void reportsWhatTheShardScored() throws Exception {
        System.out.println(SCOREBOARD.summary());
        QaEvalWorkspace.release();
    }

    @ParameterizedTest
    @MethodSource("cases")
    void judgesTheDeployAsAHumanWouldHave(QaCase deployed) {
        EvalQuorum.configured().hold(deployed.name(), this::qaSpend, () -> oneRun(deployed));
    }

    private OptionalDouble qaSpend() {
        return OptionalDouble.of(usage.sessionByKind().getOrDefault(AssistantCallKind.QA_CHECK, TokenUsage.NONE).costUsd());
    }

    private void oneRun(QaCase deployed) throws Exception {
        long started = System.nanoTime();
        OptionalDouble before = qaSpend();
        QaJudgement judged;
        try (QaFixtureEnvironment environment = QaFixtureEnvironment.serve(deployed.environment())) {
            judged = judge(deployed, environment.url());
        }
        if (judged.ending() == QaEnding.FAILED) {
            throw EvalStalled.because("the check failed: " + judged.cited());
        }
        QaScore score = QaScore.of(deployed, judged);
        SCOREBOARD.record(score, (System.nanoTime() - started) / 1e9,
                OptionalDouble.of(qaSpend().orElse(0) - before.orElse(0)));
        assertThat(score.passed()).describedAs("%s: %s", deployed.name(), score.describe()).isTrue();
    }

    private QaJudgement judge(QaCase deployed, String url) throws Exception {
        Path worktree = QaEvalWorkspace.worktreeFor(QaEvalWorkspace.root()
                .resolve("repos/" + ROUND.incrementAndGet() + "/repo"), deployed);
        String sha = deployed.deploy().sha();
        String landedProbe = deployed.environment().toString().contains("GET /version") ? url + "/version" : null;
        QaConfig qa = new QaConfig(true, System.getProperty("qaEval.model"), List.of(new QaEnvironment(url, landedProbe)));
        QaReader.Run run = deployed.declines()
                ? prompts.decline(deployed.key(), worktree.toString(), sha, qa,
                        "- " + deployed.criteria().getFirst().id() + " — not met: " + deployed.decline().evidence(),
                        deployed.decline().why())
                : prompts.accept(deployed.key(), worktree.toString(), sha, qa, QaVerdict.ACCEPT);
        QaReader.Run told = new QaReader.Run(run.shared(), run.prompt() + QaEvalWorkspace.ticket(deployed, url),
                run.worktree(), run.sha(), run.model(), run.environments());
        if (deployed.declines()) {
            return QaJudgement.ofDecline(deployed, reader.reconsider(told).facts()
                    .orElseThrow(() -> EvalStalled.because("no decline answer")));
        }
        return QaJudgement.ofVerdict(deployed, reader.read(told).facts()
                .orElseThrow(() -> EvalStalled.because("no verdict")));
    }

    static List<QaCase> cases() {
        List<QaCase> all = QaCases.all();
        return IntStream.range(0, all.size())
                .filter(index -> index % QaEvalWorkspace.shards() == QaEvalWorkspace.shard())
                .mapToObj(all::get).toList();
    }
}

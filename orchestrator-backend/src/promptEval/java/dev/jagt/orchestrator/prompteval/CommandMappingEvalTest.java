package dev.jagt.orchestrator.prompteval;

import dev.jagt.orchestrator.eval.EvalPorts;
import dev.jagt.orchestrator.eval.EvalQuorum;
import dev.jagt.orchestrator.eval.EvalStalled;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.port.CommandAssistant.CommandProposal;
import dev.jagt.orchestrator.surface.board.PaletteContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.Deployment;
import java.util.List;
import dev.jagt.orchestrator.task.TaskState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.DoubleAdder;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("promptEval")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {"spring.config.import=",
        "orchestrator.startup-checks=false", "orchestrator.open-terminal-window=false"})
class CommandMappingEvalTest {

    @MockitoBean
    private UserNotifier userNotifier;

    @TempDir
    static Path workspace;

    private static final java.util.concurrent.atomic.AtomicBoolean SEEDED = new java.util.concurrent.atomic.AtomicBoolean();

    @DynamicPropertySource
    static void orchestratorLivesInTheTempWorkspace(DynamicPropertyRegistry registry) throws Exception {
        int port = EvalPorts.free();
        registry.add("server.port", () -> port);
        registry.add("orchestrator.gate-url", () -> "http://127.0.0.1:" + port + "/api/agent");
        java.nio.file.Files.createDirectories(workspace.resolve("root"));
        registry.add("orchestrator.root", () -> workspace.resolve("root").toString());
        registry.add("orchestrator.config-file", () -> workspace.resolve("root/jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> workspace.resolve("root/state.json").toString());
    }

    @Autowired
    private StateService stateService;
    @Autowired
    private PaletteContext board;
    @Autowired
    private CommandAssistant assistant;

    @BeforeEach
    void fourTasksBetweenThemOfferingEveryVerbTheRowsAskFor() {
        synchronized (SEEDED) {
            if (SEEDED.getAndSet(true)) {
                return;
            }
            stateService.putTask("ABC-23", TaskState.builder("shop", workspace.resolve("ABC-23").toString(),
                            TaskStatus.PLAN_PENDING)
                    .alias("a4").title("Search ignores accented letters").build());
            stateService.putTask("ABC-42", TaskState.builder("shop", workspace.resolve("ABC-42").toString(),
                            TaskStatus.REVIEW_PENDING)
                    .alias("a1").title("Login form rejects a valid e-mail")
                    .mrUrl("https://example.invalid/shop/merge_requests/1").build());
            stateService.putTask("ABC-7", TaskState.builder("shop", workspace.resolve("ABC-7").toString(),
                            TaskStatus.CI_POLLING)
                    .alias("a2").title("Cart total ignores the discount")
                    .mrUrl("https://example.invalid/shop/merge_requests/2").build());
            stateService.putTask("ABC-15", TaskState.builder("shop", workspace.resolve("ABC-15").toString(),
                            TaskStatus.DEPLOYED)
                    .alias("a3").title("Checkout times out on a slow network")
                    .mrUrl("https://example.invalid/shop/merge_requests/3").deployment(new Deployment(1, List.of(Deployment.Target.of("shop", "dev").withMerge("c0ffee1")), 1, null)).build());
        }
    }

    @ParameterizedTest
    @MethodSource("cases")
    void mapsTheOperatorsOwnWordsOntoOneCommandAndOneTask(CommandMappingCase expected) {
        DoubleAdder paid = new DoubleAdder();
        EvalQuorum.configured().hold(expected.request(), () -> OptionalDouble.of(paid.sum()), () -> {
            Answer<CommandProposal> answer = assistant.mapCommand(expected.request(), board.menu());
            paid.add(answer.usage().costUsd());

            if (answer.facts().isEmpty()) {
                throw EvalStalled.because("no proposal: " + answer.failure());
            }
            assertThat(answer.facts().orElseThrow().command()).isEqualTo(expected.command());
            assertThat(answer.facts().orElseThrow().task()).isIn(expected.taskId(), expected.taskAlias());
            assertThat(answer.facts().orElseThrow().ticket()).isEqualTo(expected.ticket());
        });
    }

    static java.util.List<CommandMappingCase> cases() {
        return CommandMappingCase.matrix();
    }
}

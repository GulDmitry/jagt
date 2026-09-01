package dev.jagt.orchestrator.prompteval;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.MasterAssistant.CommandProposal;
import dev.jagt.orchestrator.service.MeteredAssistant;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
import dev.jagt.orchestrator.service.StateService;
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

import static org.assertj.core.api.Assertions.assertThat;

@Tag("promptEval")
@SpringBootTest(properties = {"spring.config.import=", "orchestrator.startup-checks=false",
        "orchestrator.open-terminal-window=false"})
class CommandMappingEvalTest {

    @TempDir
    static Path workspace;

    @DynamicPropertySource
    static void orchestratorLivesInTheTempWorkspace(DynamicPropertyRegistry registry) throws Exception {
        java.nio.file.Files.createDirectories(workspace.resolve("root"));
        registry.add("orchestrator.root", () -> workspace.resolve("root").toString());
        registry.add("orchestrator.config-file", () -> workspace.resolve("root/jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> workspace.resolve("root/state.json").toString());
    }

    @Autowired
    private StateService stateService;
    @Autowired
    private NaturalLanguageDispatch dispatch;
    @Autowired
    private MeteredAssistant assistant;

    @BeforeEach
    void threeTasksBetweenThemOfferingEveryVerbTheRowsAskFor() {
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
                .mrUrl("https://example.invalid/shop/merge_requests/3").deployCommit("c0ffee1").build());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void mapsTheOperatorsOwnWordsOntoOneCommandAndOneTask(CommandMappingCase expected) {
        Answer<CommandProposal> answer = assistant.mapCommand(expected.request(), dispatch.context());

        assertThat(answer.facts()).isPresent();
        assertThat(answer.facts().orElseThrow().command()).isEqualTo(expected.command());
        assertThat(answer.facts().orElseThrow().task()).isIn(expected.taskId(), expected.taskAlias());
    }

    static java.util.List<CommandMappingCase> cases() {
        return CommandMappingCase.matrix();
    }
}

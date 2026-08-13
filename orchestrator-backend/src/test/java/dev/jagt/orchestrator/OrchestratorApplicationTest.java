package dev.jagt.orchestrator;

import dev.jagt.orchestrator.assistant.MasterAssistant;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.mcp.McpController;
import dev.jagt.orchestrator.service.IdeRecentProjectsCleaner;
import dev.jagt.orchestrator.service.MeteredAssistant;
import dev.jagt.orchestrator.service.StateViews;
import dev.jagt.orchestrator.shell.MasterShell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The only test that boots the real application context. Every other test builds its collaborators by
 * hand, so a new bean, a missing dependency or a cycle breaks startup while the whole suite stays green —
 * the failure then surfaces as a dead jar on the human's terminal.
 *
 * <p>Two beans are mocked, both because a real one would act on the DEVELOPER'S machine rather than on the
 * test: {@link MasterShell} is an {@link org.springframework.boot.ApplicationRunner} that takes over the
 * terminal and blocks, and {@link IdeRecentProjectsCleaner} runs on a 60s schedule that rewrites the real
 * IDE's recent-projects file. Everything they depend on is still wired and asserted here.
 */
@SpringBootTest(properties = "orchestrator.open-warp-window=false")
class OrchestratorApplicationTest {

    @TempDir
    static Path root;

    @MockitoBean
    private MasterShell masterShell;
    @MockitoBean
    private IdeRecentProjectsCleaner ideRecentProjectsCleaner;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> root.toString());
        // The root alone is not enough: both files are ALSO settable on their own (ORCHESTRATOR_CONFIG_FILE
        // / ORCHESTRATOR_STATE_FILE in application.yml), and they win over the root when a developer has
        // either exported — which would point this context straight at their live files.
        registry.add("orchestrator.config-file", () -> root.resolve("config.json").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
    }

    @Test
    void theApplicationStartsWithEveryBeanWiredSoAStartupBreakFailsHereAndNotOnLaunch() {
        assertThat(context.getBean(McpController.class)).isNotNull();
        assertThat(context.getBean(StateViews.class)).isNotNull();
        assertThat(context.getBean(MeteredAssistant.class)).isNotNull();
        // Self-check on the isolation above: without it this context would run against the developer's real
        // state.json, and the test would pass while quietly touching live data. Compared as strings because
        // AssertJ's Path.startsWith resolves the real path, and state.json does not exist until written —
        // which is itself worth knowing: merely starting up touches no state.
        assertThat(context.getBean(OrchestratorPaths.class).stateFile().toString())
                .startsWith(root.toString());
    }

    @Test
    void reachesTheAssistantOnlyThroughItsMeterSoNoCallerCanSpendOffTheBooks() {
        // A second MasterAssistant bean would make the injection point ambiguous — a compile-clean,
        // wiring-clean mistake. (That a caller injects the METER rather than the port cannot be asserted from
        // here: MeteredAssistant deliberately does not implement the interface, so it is not even a candidate.)
        assertThat(context.getBeansOfType(MasterAssistant.class)).hasSize(1);
    }
}

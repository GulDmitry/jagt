package dev.jagt.orchestrator;

import dev.jagt.orchestrator.port.MasterAssistant;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.surface.mcp.McpController;
import dev.jagt.orchestrator.service.IdeRecentProjectsCleaner;
import dev.jagt.orchestrator.service.MeteredAssistant;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.surface.ui.LogFileReset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.config.import=",
        "orchestrator.open-terminal-window=false", "orchestrator.startup-checks=false"})
@ResourceLock("spring-logging")
class OrchestratorApplicationTest {

    @TempDir
    static Path root;

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
        registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
    }

    @Test
    void theApplicationStartsWithEveryBeanWiredSoAStartupBreakFailsHereAndNotOnLaunch() {
        assertThat(context.getBean(McpController.class)).isNotNull();
        assertThat(context.getBean(GlobalCommands.class)).isNotNull();
        assertThat(context.getBean(MeteredAssistant.class)).isNotNull();
    }

    @Test
    void keepsTheDevelopersOwnStateFileOutOfABootedContext() {
        assertThat(context.getBean(OrchestratorPaths.class).stateFile().toString())
                .startsWith(root.toString());
    }

    @Test
    void launchesWithTheLogFileResetListenerRegistered() {
        assertThat(OrchestratorApplication.application().getListeners())
                .hasAtLeastOneElementOfType(LogFileReset.class);
    }

    @Test
    void reachesTheAssistantOnlyThroughItsMeterSoNoCallerCanSpendOffTheBooks() {
        assertThat(context.getBeansOfType(MasterAssistant.class)).hasSize(1);
    }
}

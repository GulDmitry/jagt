package dev.jagt.orchestrator;

import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.surface.mcp.McpController;
import dev.jagt.orchestrator.service.TicketReader;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.surface.ui.LogFileRoll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.config.import=",
        "orchestrator.open-terminal-window=false", "orchestrator.startup-checks=false",
        "orchestrator.usage-limits.enabled=false"})
@ResourceLock("spring-logging")
class OrchestratorApplicationTest {

    @MockitoBean
    private UserNotifier userNotifier;

    @TempDir
    static Path root;

    @MockitoBean
    private EditorDriver editorDriver;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void keepConfigAndStateOutOfTheDevelopersOwnFilesEvenWhereTheyExportedThemOverTheRoot(
            DynamicPropertyRegistry registry) throws IOException {
        Files.copy(Path.of("../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        Files.copy(Path.of("../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        registry.add("orchestrator.root", () -> root.toString());
        registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
    }

    @Test
    void theApplicationStartsWithEveryBeanWiredSoAStartupBreakFailsHereAndNotOnLaunch() {
        assertThat(context.getBean(McpController.class)).isNotNull();
        assertThat(context.getBean(GlobalCommands.class)).isNotNull();
        assertThat(context.getBean(TicketReader.class)).isNotNull();
    }

    @Test
    void keepsTheDevelopersOwnStateFileOutOfABootedContext() {
        assertThat(context.getBean(OrchestratorPaths.class).stateFile().toString())
                .startsWith(root.toString());
    }

    @Test
    void launchesWithTheLogFileRollListenerRegistered() {
        assertThat(OrchestratorApplication.application().getListeners())
                .hasAtLeastOneElementOfType(LogFileRoll.class);
    }
}

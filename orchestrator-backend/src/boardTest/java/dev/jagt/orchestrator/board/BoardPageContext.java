package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import dev.jagt.orchestrator.command.ReviewRepliesReport;
import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.surface.board.NaturalLanguageDispatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=",
                "orchestrator.open-terminal-window=false", "orchestrator.startup-checks=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class BoardPageContext {

    @TempDir
    static Path root;

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    StateService state;

    @MockitoBean
    CommandService commands;
    @MockitoBean
    TaskLauncher launcher;
    @MockitoBean
    NaturalLanguageDispatch naturalLanguage;
    @MockitoBean
    EditorDriver editorDriver;
    @MockitoBean
    AgentSessions sessions;
    @MockitoSpyBean
    ReviewRepliesReport replies;

    BrowserContext session;

    @DynamicPropertySource
    static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> root.toString());
        registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
        registry.add("logging.file.name", () -> root.resolve("jagt.log").toString());
    }

    @BeforeAll
    static void startTheBrowserAndNameTheProjectsTheBoardOffers() throws IOException {
        Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:
                  projects:
                    alpha: {path: "%s", baseBranch: origin/main, deployBranch: dev}
                    beta: {path: "%s", baseBranch: origin/main, deployBranch: dev}
                  autoReview: {enabled: true}
                """.formatted(root.resolve("alpha"), root.resolve("beta")));
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void stopTheBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void emptyTheBoard() throws IOException {
        Files.deleteIfExists(root.resolve("state.json"));
        Files.deleteIfExists(root.resolve("state.json.bak"));
        session = browser.newContext();
    }

    @AfterEach
    void closeTheTab() {
        session.close();
    }
}

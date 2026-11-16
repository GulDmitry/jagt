package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.command.ReviewRepliesReport;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.scenario.OpenedWindows;
import dev.jagt.orchestrator.surface.board.task.NaturalLanguageDispatch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskState.TaskStateBuilder;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.import=",
      "orchestrator.terminal=none",
      "orchestrator.startup-checks=false",
      "orchestrator.usage-limits.enabled=false"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(BoardPageContext.BreakableJob.class)
@ExtendWith(BoardBrowser.class)
abstract class BoardPageContext {

  @MockitoBean UserNotifier userNotifier;

  static class BreakableJob implements Job {
    private volatile boolean broken;

    void breakIt() {
      broken = true;
    }

    @Override
    public String id() {
      return "breakable";
    }

    @Override
    public String describe() {
      return "fails once broken";
    }

    @Override
    public Duration every() {
      return Duration.ofMinutes(1);
    }

    @Override
    public void run() {
      if (broken) {
        throw new IllegalStateException("job broken");
      }
    }
  }

  final BreakableJob breakable;

  @TempDir static Path root;

  @LocalServerPort int port;

  final StateService state;

  @Autowired
  BoardPageContext(BreakableJob breakable, StateService state) {
    this.breakable = breakable;
    this.state = state;
  }

  @MockitoBean CommandService commands;
  @MockitoBean TaskLauncher launcher;
  @MockitoBean NaturalLanguageDispatch naturalLanguage;
  @MockitoBean EditorDriver editorDriver;
  @MockitoBean AgentSessions sessions;
  @MockitoBean OpenedWindows windows;
  @MockitoBean SessionHost sessionHost;
  @MockitoSpyBean ReviewRepliesReport replies;

  BrowserContext session;

  @DynamicPropertySource
  static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry) {
    registry.add("orchestrator.root", () -> root.toString());
    registry.add("orchestrator.data", () -> root.toString());
    registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
    registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
    registry.add("logging.file.name", () -> root.resolve("jagt.log").toString());
  }

  @BeforeAll
  static void nameTheProjectsTheBoardOffers() throws IOException {
    try (var shipped = Files.newDirectoryStream(Path.of(".."), "*.dist")) {
      for (Path file : shipped) {
        Files.copy(
            file,
            root.resolve(file.getFileName()),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    }
    try (var shipped = Files.walk(Path.of("..", "roles"))) {
      for (Path file : shipped.filter(path -> path.toString().endsWith(".dist")).toList()) {
        Path target = root.resolve(Path.of("..").relativize(file));
        Files.createDirectories(target.getParent());
        Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    }
    Files.writeString(
        root.resolve("jagt.yml"),
        """
        orchestrator:
          projects:
            beta: {path: "%s", baseBranch: origin/main, deployBranch: dev}
            alpha: {path: "%s", baseBranch: origin/main, deployBranch: dev}
          autoReview: {enabled: true}
        """
            .formatted(root.resolve("beta"), root.resolve("alpha")));
  }

  TaskStateBuilder alphaTask(String key, TaskStatus status) {
    return TaskState.builder("alpha", root.resolve(key + "-alpha").toString(), status)
        .alias("a" + key.substring("ABC-".length()))
        .lastActiveTimestamp(System.currentTimeMillis());
  }

  void putReviewTask(String key) {
    state.putTask(
        key, alphaTask(key, TaskStatus.REVIEW_PENDING).mrUrl("https://host.example/mr/7").build());
  }

  void ask(Page page, String question) {
    page.keyboard().press("Control+k");
    page.locator("#ask").fill(question);
    page.locator("#ask").press("Enter");
  }

  Locator button(Page page, String name) {
    return page.getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
  }

  CompletableFuture<String> dismissedDialogMessage(Page page) {
    CompletableFuture<String> asked = new CompletableFuture<>();
    page.onDialog(dialog -> {
      asked.complete(dialog.message());
      dialog.dismiss();
    });
    return asked;
  }

  Page openLiveBoard() {
    Page page = openBoard();
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    return page;
  }

  Page openBoard() {
    Page page = session.newPage();
    page.navigate("http://localhost:" + port + "/");
    return page;
  }

  @BeforeEach
  void emptyTheBoard(Browser browser) throws IOException {
    Files.deleteIfExists(root.resolve("state.json"));
    Files.deleteIfExists(root.resolve("state.json.bak"));
    session = browser.newContext();
  }

  @AfterEach
  void closeTheTab() {
    session.close();
  }
}

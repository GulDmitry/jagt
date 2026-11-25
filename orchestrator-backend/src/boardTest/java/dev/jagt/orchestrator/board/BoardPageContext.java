package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.command.ReviewRepliesReport;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.scenario.OpenedWindows;
import dev.jagt.orchestrator.service.session.SessionStreams;
import dev.jagt.orchestrator.surface.board.task.NaturalLanguageDispatch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskState.TaskStateBuilder;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.FileSystemUtils;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.import=",
      "orchestrator.terminal=none",
      "orchestrator.startup-checks=false",
      "orchestrator.usage-limits.enabled=false"
    })
@Import(BoardPageContext.BreakableJob.class)
@ExtendWith(BoardBrowser.class)
abstract class BoardPageContext {

  @MockitoBean UserNotifier userNotifier;

  static class BreakableJob implements Job {
    private volatile boolean broken;
    private final ObjectProvider<Jobs> jobs;

    BreakableJob(ObjectProvider<Jobs> jobs) {
      this.jobs = jobs;
    }

    void breakIt() {
      broken = true;
    }

    boolean repair() {
      boolean was = broken;
      broken = false;
      if (was) {
        jobs.getObject().runNow(id());
      }
      return was;
    }

    boolean failing() {
      return jobs.getObject().statuses(System.currentTimeMillis()).stream()
          .anyMatch(status -> status.id().equals(id()) && status.lastError() != null);
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
    public @Nullable Duration every() {
      return null;
    }

    @Override
    public void run() {
      if (broken) {
        throw new IllegalStateException("job broken");
      }
    }
  }

  final BreakableJob breakable;

  static final Path root = createRoot();

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
  @MockitoBean SessionStreams streams;
  @MockitoBean SessionHost sessionHost;
  @MockitoBean McpHealth mcpHealth;
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

  private static Path createRoot() {
    try {
      Path created = Files.createTempDirectory("board-root");
      nameTheProjectsTheBoardOffers(created);
      return created;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void nameTheProjectsTheBoardOffers(Path folder) throws IOException {
    try (var shipped = Files.newDirectoryStream(Path.of(".."), "*.dist")) {
      for (Path file : shipped) {
        replaceAtomically(file, folder.resolve(file.getFileName()));
      }
    }
    try (var shipped = Files.walk(Path.of("..", "roles"))) {
      for (Path file : shipped.filter(path -> path.toString().endsWith(".dist")).toList()) {
        Path target = folder.resolve(Path.of("..").relativize(file));
        Files.createDirectories(target.getParent());
        replaceAtomically(file, target);
      }
    }
    Path written = folder.resolve("jagt.yml.tmp");
    Files.writeString(
        written,
        """
        orchestrator:
          projects:
            beta: {path: "%s", baseBranch: origin/main, deployBranch: dev}
            alpha: {path: "%s", baseBranch: origin/main, deployBranch: dev}
          autoReview: {enabled: true}
        """
            .formatted(folder.resolve("beta"), folder.resolve("alpha")));
    Files.move(written, folder.resolve("jagt.yml"), StandardCopyOption.ATOMIC_MOVE);
  }

  private static void replaceAtomically(Path source, Path target) throws IOException {
    Path staged = target.resolveSibling(target.getFileName() + ".tmp");
    Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
    Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
  }

  private static boolean shipped(Path file) {
    String name = file.getFileName().toString();
    return name.endsWith(".dist") || name.equals("jagt.yml") || name.equals("jagt.log");
  }

  private static void removeTestAddedFiles(Path entry) throws IOException {
    if (Files.isDirectory(entry) && entry.getFileName().toString().equals("roles")) {
      try (var files = Files.walk(entry)) {
        for (Path file : files.filter(Files::isRegularFile).filter(f -> !shipped(f)).toList()) {
          Files.delete(file);
        }
      }
    } else if (!shipped(entry)) {
      FileSystemUtils.deleteRecursively(entry);
    }
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

  AtomicReference<String> dismissedDialogMessage(Page page) {
    AtomicReference<String> asked = new AtomicReference<>();
    page.onDialog(dialog -> {
      asked.set(dialog.message());
      dialog.dismiss();
    });
    return asked;
  }

  Page openLiveBoard() {
    Page page = openBoard();
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    return page;
  }

  Page openRoleLog(String role, String slice) {
    Page page = session.newPage();
    page.route(
        "**/api/tasks",
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setResponse(route.fetch())
                    .setBody(
                        route
                            .fetch()
                            .text()
                            .replaceFirst(
                                "\"roleLogs\":\\[[^\\]]*\\]", "\"roleLogs\":[\"" + role + "\"]"))));
    page.route(
        Pattern.compile(".*/api/roles/" + role + "/log.*"),
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setContentType("application/json")
                    .setBody(slice)));
    page.navigate("http://localhost:" + port + "/");
    page.locator("#role-logs button.chip").click();
    return page;
  }

  Page openBoard() {
    Page page = session.newPage();
    page.navigate("http://localhost:" + port + "/");
    return page;
  }

  @BeforeEach
  void emptyTheBoard(BrowserContext context) throws IOException {
    try (var entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        removeTestAddedFiles(entry);
      }
    }
    nameTheProjectsTheBoardOffers(root);
    session = context;
  }

  @AfterEach
  void closeTheTab() {
    if (breakable.repair()) {
      session.newPage().waitForCondition(() -> !breakable.failing());
    }
  }
}

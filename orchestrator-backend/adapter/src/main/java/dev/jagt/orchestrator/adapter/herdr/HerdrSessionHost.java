package dev.jagt.orchestrator.adapter.herdr;

import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.adapter.terminal.AbstractSessionHost;
import dev.jagt.orchestrator.adapter.terminal.ExitedWindow;
import dev.jagt.orchestrator.adapter.terminal.SessionEnvironment;
import dev.jagt.orchestrator.adapter.terminal.ShellWords;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.OneLine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Windows are tabs of one workspace, labelled with the window's name. A tab has no command of its
 * own, so the line is typed into its shell, which it replaces.
 */
@Service
@ConditionalOnProperty(name = "orchestrator.session-host", havingValue = "herdr")
@Slf4j
public class HerdrSessionHost extends AbstractSessionHost {

  private static final String TYPED_LINE = "exec sh -c ";

  private final HerdrApi api;
  private final HerdrReads reads;
  private final SessionEnvironment environment;
  private final TerminalDriver terminalDriver;
  private final Object lock = new Object();

  public HerdrSessionHost(
      HerdrApi api,
      HerdrReads reads,
      SessionEnvironment environment,
      TerminalDriver terminalDriver,
      AgentRuntime agentRuntime) {
    super(agentRuntime);
    this.api = api;
    this.reads = reads;
    this.environment = environment;
    this.terminalDriver = terminalDriver;
  }

  @Override
  protected void taskWindow(
      String session,
      String dedicatedTitle,
      String taskId,
      @Nullable String alias,
      Path worktreePath,
      String command) {
    synchronized (lock) {
      String workspace = ensureSession(session);
      killTaskWindows(session, taskId);
      HerdrReads.Window window =
          newWindow(
              session, workspace, taskId, worktreePath, environment.prefix(worktreePath) + command);
      log.atInfo()
          .setMessage("task window opened")
          .addKeyValue("task", taskId)
          .addKeyValue("window", window.tabId())
          .log();
      if (alias != null && !alias.isBlank()) {
        api.call(session, "pane.rename", Map.of("pane_id", window.paneId(), "label", alias));
      }
      ensureViewer(session, dedicatedTitle);
    }
  }

  @Override
  public void openCommandWindow(
      String session, String dedicatedTitle, String name, List<String> command) {
    List<String> resolved = new ArrayList<>(command);
    resolved.set(0, Executables.resolve(command.getFirst()));
    shown(session, dedicatedTitle, name, environment.viewerDir(), ShellWords.joined(resolved));
  }

  @Override
  public void openShellWindow(
      String session, String dedicatedTitle, String name, Path cwd, String command) {
    shown(session, dedicatedTitle, name, cwd, command);
  }

  private void shown(String session, String dedicatedTitle, String name, Path cwd, String line) {
    synchronized (lock) {
      String workspace = ensureSession(session);
      killTaskWindows(session, name);
      HerdrReads.Window window = newWindow(session, workspace, name, cwd, line);
      api.call(session, "tab.focus", Map.of("tab_id", window.tabId()));
      ensureViewer(session, dedicatedTitle);
    }
  }

  private HerdrReads.Window newWindow(
      String session, String workspace, String name, Path cwd, String command) {
    JsonNode created =
        api.call(
            session,
            "tab.create",
            Map.of(
                "workspace_id", workspace, "label", name, "cwd", cwd.toString(), "focus", false));
    reads.changed();
    HerdrReads.Window window =
        new HerdrReads.Window(
            created.path("tab").path("tab_id").asString(""),
            created.path("root_pane").path("pane_id").asString(""),
            name);
    try {
      api.call(
          session,
          "pane.send_text",
          Map.of(
              "pane_id",
              window.paneId(),
              "text",
              TYPED_LINE + ShellWords.quoted(ExitedWindow.shellLine(command)) + "\n"));
    } catch (HerdrApi.Failed | IllegalStateException failed) {
      closeTab(session, window.tabId());
      throw failed;
    }
    reads.started(window);
    return window;
  }

  @Override
  public boolean focusTaskWindow(String session, String dedicatedTitle, String taskId) {
    synchronized (lock) {
      Optional<HerdrReads.Window> window = find(session, taskId);
      if (window.isEmpty()) {
        return false;
      }
      api.call(session, "tab.focus", Map.of("tab_id", window.get().tabId()));
      ensureViewer(session, dedicatedTitle);
      return true;
    }
  }

  @Override
  public WindowState taskWindowState(String session, String taskId) {
    synchronized (lock) {
      Optional<HerdrReads.Window> window = find(session, taskId);
      if (window.isEmpty()) {
        return WindowState.MISSING;
      }
      return api.ifPaneThere(() -> agentRunning(session, window.get()))
          .map(running -> running ? WindowState.AGENT_RUNNING : WindowState.DEAD_SHELL)
          .orElse(WindowState.MISSING);
    }
  }

  @Override
  public Optional<String> exitTail(String session, String taskId) {
    synchronized (lock) {
      return find(session, taskId)
          .flatMap(window ->
              api.ifPaneThere(() ->
                  agentRunning(session, window)
                      ? Optional.<String>empty()
                      : ExitedWindow.printedBeforeExit(read(session, window, false))))
          .flatMap(tail -> tail);
    }
  }

  @Override
  public Optional<String> screenOf(String session, String taskId) {
    synchronized (lock) {
      return find(session, taskId)
          .flatMap(window -> api.ifPaneThere(() -> read(session, window, true)));
    }
  }

  private String read(String session, HerdrReads.Window window, boolean styled) {
    return api.call(
            session,
            "pane.read",
            Map.of(
                "pane_id",
                window.paneId(),
                "source",
                "visible",
                "format",
                styled ? "ansi" : "text",
                "strip_ansi",
                !styled))
        .path("read")
        .path("text")
        .asString("");
  }

  @Override
  public boolean nudgeTaskWindow(String session, String taskId, String message) {
    return toPane(
        session,
        taskId,
        window ->
            api.call(
                session,
                "pane.send_input",
                Map.of("pane_id", window.paneId(), "text", message, "keys", List.of("enter"))));
  }

  @Override
  public boolean submitTaskWindow(String session, String taskId) {
    return pressTaskWindow(session, taskId, Key.ENTER);
  }

  @Override
  public boolean pressTaskWindow(String session, String taskId, Key key) {
    return toPane(
        session,
        taskId,
        window ->
            api.call(
                session,
                "pane.send_keys",
                Map.of("pane_id", window.paneId(), "keys", List.of(keyName(key)))));
  }

  static String keyName(Key key) {
    return switch (key) {
      case ESCAPE -> "esc";
      case UP -> "up";
      case DOWN -> "down";
      case LEFT -> "left";
      case RIGHT -> "right";
      case TAB -> "tab";
      case SPACE -> "space";
      case ENTER -> "enter";
      case CTRL_C -> "ctrl+c";
      case CTRL_U -> "ctrl+u";
      case DIGIT_1 -> "1";
      case DIGIT_2 -> "2";
      case DIGIT_3 -> "3";
      case DIGIT_4 -> "4";
      case DIGIT_5 -> "5";
      case DIGIT_6 -> "6";
      case DIGIT_7 -> "7";
      case DIGIT_8 -> "8";
      case DIGIT_9 -> "9";
    };
  }

  private boolean toPane(String session, String taskId, Consumer<HerdrReads.Window> send) {
    synchronized (lock) {
      Optional<HerdrReads.Window> window = find(session, taskId);
      if (window.isEmpty()) {
        return false;
      }
      boolean sent =
          api.ifPaneThere(
                  () -> {
                    send.accept(window.get());
                    return true;
                  })
              .isPresent();
      if (sent) {
        reads.changed();
      }
      return sent;
    }
  }

  /** Closing a tab also ends its processes, the agent with them. */
  @Override
  public int killTaskWindows(String session, String taskId) {
    synchronized (lock) {
      int killed = 0;
      for (HerdrReads.Window window : reads.windows(session)) {
        if (window.name().equals(taskId) && closeTab(session, window.tabId())) {
          killed++;
          reads.forget(session, taskId, window.paneId());
          log.atInfo()
              .setMessage("task window closed")
              .addKeyValue("task", taskId)
              .addKeyValue("window", window.tabId())
              .log();
        }
      }
      reads.changed();
      return killed;
    }
  }

  private boolean closeTab(String session, String tabId) {
    try {
      api.call(session, "tab.close", Map.of("tab_id", tabId));
      return true;
    } catch (HerdrApi.Failed | IllegalStateException failed) {
      log.atWarn()
          .setMessage("herdr tab close failed")
          .addKeyValue("window", tabId)
          .addKeyValue("cause", OneLine.clipped(Causes.of(failed)))
          .setCause(failed)
          .log();
      return false;
    }
  }

  @Override
  public long lastWindowActivityMillis(String session, String taskId) {
    synchronized (lock) {
      Optional<HerdrReads.Window> window = find(session, taskId);
      if (window.isEmpty()) {
        reads.forget(session, taskId, "");
        return 0L;
      }
      return reads.activityMillis(session, window.get());
    }
  }

  private Optional<HerdrReads.Window> find(String session, String taskId) {
    return reads.windows(session).stream()
        .filter(window -> window.name().equals(taskId))
        .findFirst();
  }

  /**
   * Anything in the foreground but the pane's own process is the agent; a line typed and not yet
   * exited, or not yet echoed within the start window, is the agent about to be.
   */
  private boolean agentRunning(String session, HerdrReads.Window window) {
    JsonNode info =
        api.call(session, "pane.process_info", Map.of("pane_id", window.paneId()))
            .path("process_info");
    JsonNode shell = info.path("shell_pid");
    if (!shell.isNumber()) {
      throw new WindowsUnreadable("herdr pane " + window.paneId() + " reports no shell process");
    }
    boolean foreign =
        info.path("foreground_processes")
            .valueStream()
            .anyMatch(process -> process.path("pid").asLong() != shell.asLong());
    if (foreign) {
      return true;
    }
    String screen = read(session, window, false);
    if (screen.contains(TYPED_LINE)) {
      return !ExitedWindow.hasExited(screen);
    }
    return reads.starting(window);
  }

  private String ensureSession(String session) {
    api.ensureServer(session);
    return reads
        .workspaceId(session)
        .orElseGet(() -> {
          String id =
              api.call(
                      session,
                      "workspace.create",
                      Map.of(
                          "label", session, "cwd", environment.root().toString(), "focus", false))
                  .path("workspace")
                  .path("workspace_id")
                  .asString("");
          reads.changed();
          return id;
        });
  }

  private void ensureViewer(String session, String dedicatedTitle) {
    if (!api.viewerAttached(session)) {
      terminalDriver.openViewer(
          api.attachCommand(session), dedicatedTitle, environment.viewerDir());
    }
  }
}

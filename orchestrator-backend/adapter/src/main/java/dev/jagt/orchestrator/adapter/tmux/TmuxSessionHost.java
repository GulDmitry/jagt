package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import dev.jagt.orchestrator.service.OneLine;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TmuxSessionHost implements SessionHost {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  /** Only what is shown is cut: the full window name stays the task's identity. */
  private static final String SHOWN_NAME = "#{=/10/…:window_name}";

  /**
   * The default is #I:#W#F; the trailing #F renders the window flags (`*` current, `-` last, `Z`
   * zoomed).
   */
  private static final String WINDOW_FORMAT =
      "#I:" + SHOWN_NAME + "#{?#{@jagt_alias}, (#{@jagt_alias}),}#F";

  private static final String WINDOW_OPTIONS =
      "set-option -w window-status-format \""
          + WINDOW_FORMAT
          + "\" ; set-option -w window-status-current-format \""
          + WINDOW_FORMAT
          + "\" ; set-option -w window-status-current-style \"bg=colour160,fg=colour231,bold\"";
  private static final String EXITED = "[jagt] agent exited";
  private static final int TAIL_LINES = 3;

  private final Processes processRunner;
  private final TmuxReads reads;
  private final SessionEnvironment environment;
  private final TerminalDriver terminalDriver;
  private final AgentRuntime agentRuntime;
  private final Object lock = new Object();

  @Override
  public Optional<String> unaddressable(String name) {
    return reserved(name);
  }

  /**
   * tmux addresses a window as {@code session:window.pane}, so a name carrying either is
   * unaddressable.
   */
  static Optional<String> reserved(String name) {
    return name.chars().anyMatch(c -> c == ':' || c == '.')
        ? Optional.of("contains ':' or '.', which tmux reserves")
        : Optional.empty();
  }

  @Override
  public String sessionName(@Nullable String configured) {
    return configured == null || configured.isBlank() ? "jagt" : configured;
  }

  @Override
  public void openTaskWindow(
      String session,
      String dedicatedTitle,
      String taskId,
      @Nullable String alias,
      Path worktreePath,
      boolean planMode,
      String model) {
    taskWindow(
        session,
        dedicatedTitle,
        taskId,
        alias,
        worktreePath,
        model.isBlank()
            ? agentRuntime.launchCommand(worktreePath, planMode)
            : agentRuntime.launchCommand(worktreePath, planMode, model));
  }

  @Override
  public void reviveTaskWindow(
      String session,
      String dedicatedTitle,
      String taskId,
      @Nullable String alias,
      Path worktreePath) {
    taskWindow(
        session,
        dedicatedTitle,
        taskId,
        alias,
        worktreePath,
        agentRuntime.reviveCommand(worktreePath));
  }

  private void taskWindow(
      String session,
      String dedicatedTitle,
      String taskId,
      @Nullable String alias,
      Path worktreePath,
      String command) {
    agentRuntime.beforeLaunch(worktreePath);
    synchronized (lock) {
      ensureSession(session);
      // One task = one window: respawns must never accumulate duplicates.
      killTaskWindows(session, taskId);
      String windowId =
          newWindow(session, taskId, worktreePath, environment.prefix(worktreePath) + command);
      log.atInfo()
          .setMessage("task window opened")
          .addKeyValue("task", taskId)
          .addKeyValue("window", windowId)
          .log();
      // The window name must stay the taskId; the alias rides in a window user-option.
      if (alias != null && !alias.isBlank()) {
        processRunner.run(
            null, TIMEOUT, tmux("set-option", "-w", "-t", windowId, "@jagt_alias", alias));
      }
      ensureViewer(session, dedicatedTitle);
    }
  }

  @Override
  public void openCommandWindow(
      String session, String dedicatedTitle, String name, List<String> command) {
    List<String> resolved = new ArrayList<>(command);
    resolved.set(0, Executables.resolve(command.getFirst()));
    String line =
        resolved.stream()
            .map(part -> "'" + part.replace("'", "'\\''") + "'")
            .collect(Collectors.joining(" "));
    shown(session, dedicatedTitle, name, VIEWER_CWD, line);
  }

  @Override
  public void openShellWindow(
      String session, String dedicatedTitle, String name, Path cwd, String command) {
    shown(session, dedicatedTitle, name, cwd, command);
  }

  private void shown(String session, String dedicatedTitle, String name, Path cwd, String line) {
    synchronized (lock) {
      ensureSession(session);
      killTaskWindows(session, name);
      String windowId = newWindow(session, name, cwd, line);
      processRunner.run(null, TIMEOUT, tmux("select-window", "-t", windowId));
      ensureViewer(session, dedicatedTitle);
    }
  }

  /** The window id, which is the only target immune to a name collision on a respawn. */
  private String newWindow(String session, String name, Path cwd, String command) {
    // An interactive shell here would linger forever and read as a hung process.
    // Exec'd, the tail holds no child process, so the window reads as exited while it shows why.
    String ran =
        command
            + "; printf '\\n"
            + EXITED
            + " — window closes in 15s (Ctrl+C to close now)\\n'; exec sleep 15";
    String windowId =
        processRunner
            .run(
                null,
                TIMEOUT,
                tmux(
                    "new-window",
                    "-d",
                    "-P",
                    "-F",
                    "#{window_id}",
                    "-t",
                    "=" + session + ":",
                    "-n",
                    name,
                    "-c",
                    cwd.toString(),
                    ran))
            .expectSuccess("tmux new-window " + name)
            .stdout()
            .trim();
    reads.changed();
    var rename =
        processRunner.run(
            null, TIMEOUT, tmux("set-option", "-w", "-t", windowId, "automatic-rename", "off"));
    if (rename.exitCode() != 0) {
      log.atWarn()
          .setMessage("tmux window rename failed")
          .addKeyValue("window", name)
          .addKeyValue("cause", OneLine.clipped(rename.stderr()))
          .log();
    }
    return windowId;
  }

  @Override
  public boolean focusTaskWindow(String session, String dedicatedTitle, String taskId) {
    synchronized (lock) {
      var windowId = findWindowId(session, taskId);
      if (windowId.isEmpty()) {
        return false;
      }
      processRunner
          .run(null, TIMEOUT, tmux("select-window", "-t", windowId.get()))
          .expectSuccess("tmux select-window " + windowId.get());
      ensureViewer(session, dedicatedTitle);
      return true;
    }
  }

  /**
   * {@code pane_current_command} reports the shell without job control, so detection is by child
   * processes.
   */
  @Override
  public WindowState taskWindowState(String session, String taskId) {
    synchronized (lock) {
      var window = findWindow(session, taskId);
      if (window.isEmpty()) {
        return WindowState.MISSING;
      }
      return agentRunning(window.get()) ? WindowState.AGENT_RUNNING : WindowState.DEAD_SHELL;
    }
  }

  @Override
  public Optional<String> exitTail(String session, String taskId) {
    synchronized (lock) {
      var window = findWindow(session, taskId);
      if (window.isEmpty() || agentRunning(window.get())) {
        return Optional.empty();
      }
      String shown =
          processRunner
              .run(null, TIMEOUT, tmux("capture-pane", "-p", "-t", window.get().id()))
              .expectSuccess("tmux capture-pane " + window.get().id())
              .stdout();
      return printedBeforeExit(shown);
    }
  }

  private static Optional<String> printedBeforeExit(String shown) {
    List<String> lines = shown.lines().toList();
    int exited =
        IntStream.range(0, lines.size())
            .filter(i -> lines.get(i).startsWith(EXITED))
            .max()
            .orElse(-1);
    if (exited < 0) {
      return Optional.empty();
    }
    List<String> printed =
        lines.subList(0, exited).stream().filter(line -> !line.isBlank()).toList();
    return Optional.of(
        String.join(
            "\n", printed.subList(Math.max(0, printed.size() - TAIL_LINES), printed.size())));
  }

  private boolean agentRunning(TmuxReads.Window window) {
    return reads.hasChildren(window.panePid());
  }

  @Override
  public Optional<String> screenOf(String session, String taskId) {
    synchronized (lock) {
      var windowId = findWindowId(session, taskId);
      if (windowId.isEmpty()) {
        return Optional.empty();
      }
      var screen =
          processRunner.run(null, TIMEOUT, tmux("capture-pane", "-p", "-e", "-t", windowId.get()));
      return Optional.of(screen.expectSuccess("tmux capture-pane " + windowId.get()).stdout());
    }
  }

  @Override
  public boolean nudgeTaskWindow(String session, String taskId, String message) {
    synchronized (lock) {
      var windowId = findWindowId(session, taskId);
      if (windowId.isEmpty()) {
        return false;
      }
      processRunner
          .run(null, TIMEOUT, tmux("send-keys", "-t", windowId.get(), "-l", message))
          .expectSuccess("tmux send-keys (nudge) " + taskId);
      enter(windowId.get(), taskId);
      return true;
    }
  }

  @Override
  public boolean submitTaskWindow(String session, String taskId) {
    synchronized (lock) {
      var windowId = findWindowId(session, taskId);
      windowId.ifPresent(id -> enter(id, taskId));
      return windowId.isPresent();
    }
  }

  @Override
  public boolean pressTaskWindow(String session, String taskId, Key key) {
    synchronized (lock) {
      var windowId = findWindowId(session, taskId);
      if (windowId.isEmpty()) {
        return false;
      }
      processRunner
          .run(null, TIMEOUT, tmux("send-keys", "-t", windowId.get(), keyName(key)))
          .expectSuccess("tmux send-keys " + key + " " + taskId);
      reads.changed();
      return true;
    }
  }

  static String keyName(Key key) {
    return switch (key) {
      case ESCAPE -> "Escape";
      case UP -> "Up";
      case DOWN -> "Down";
      case LEFT -> "Left";
      case RIGHT -> "Right";
      case TAB -> "Tab";
      case SPACE -> "Space";
      case ENTER -> "Enter";
      case CTRL_C -> "C-c";
      case CTRL_U -> "C-u";
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

  private void enter(String windowId, String taskId) {
    processRunner
        .run(null, TIMEOUT, tmux("send-keys", "-t", windowId, "Enter"))
        .expectSuccess("tmux send-keys Enter " + taskId);
    reads.changed();
  }

  /** Killing a window also kills its processes, so this ends the agent with it. */
  @Override
  public int killTaskWindows(String session, String taskId) {
    synchronized (lock) {
      List<String> ids =
          reads.lines(session, "#{window_id} #{window_name}").stream()
              .filter(line -> line.endsWith(" " + taskId))
              .map(line -> line.substring(0, line.indexOf(' ')))
              .toList();
      int killed = 0;
      for (String id : ids) {
        var kill = processRunner.run(null, TIMEOUT, tmux("kill-window", "-t", id));
        if (kill.exitCode() == 0) {
          killed++;
          log.atInfo()
              .setMessage("task window closed")
              .addKeyValue("task", taskId)
              .addKeyValue("window", id)
              .log();
        } else {
          log.atWarn()
              .setMessage("tmux kill-window failed")
              .addKeyValue("window", id)
              .addKeyValue("task", taskId)
              .addKeyValue("cause", OneLine.clipped(kill.stderr()))
              .log();
        }
      }
      reads.changed();
      return killed;
    }
  }

  /**
   * Epoch-millis of the window's last terminal output; 0 when the window is gone; unreadable
   * throws.
   */
  @Override
  public long lastWindowActivityMillis(String session, String taskId) {
    synchronized (lock) {
      return findWindow(session, taskId).map(window -> window.activitySeconds() * 1000L).orElse(0L);
    }
  }

  private Optional<String> findWindowId(String session, String taskId) {
    return findWindow(session, taskId).map(TmuxReads.Window::id);
  }

  private Optional<TmuxReads.Window> findWindow(String session, String taskId) {
    return reads.windows(session).stream()
        .filter(window -> window.name().equals(taskId))
        .findFirst();
  }

  private void ensureSession(String session) {
    var has = processRunner.run(null, TIMEOUT, tmux("has-session", "-t", "=" + session));
    if (has.exitCode() != 0) {
      processRunner
          .run(
              null,
              TIMEOUT,
              tmux("new-session", "-d", "-s", session, "-c", environment.root().toString()))
          .expectSuccess("tmux new-session " + session);
    }
    // One invocation: this runs every few seconds under the lock every other window call waits on.
    var options =
        processRunner.run(
            null,
            TIMEOUT,
            tmux(
                // No ESC delay, which makes TUIs feel sluggish.
                "set-option",
                "-sg",
                "escape-time",
                "0",
                ";",
                "set-option",
                "-g",
                "focus-events",
                "on",
                ";",
                "set-option",
                "-t",
                session,
                "mouse",
                "on",
                ";",
                // A title-aware terminal decorates its tab with the active window name.
                "set-option",
                "-t",
                session,
                "set-titles",
                "on",
                ";",
                // "taskId (alias)" when the window carries an alias, else just the window name.
                "set-option",
                "-t",
                session,
                "set-titles-string",
                "#{?#{@jagt_alias}," + SHOWN_NAME + " (#{@jagt_alias})," + SHOWN_NAME + "}",
                ";",
                // Two levels, both lost in tmux's black-on-green default: the bar reads as a bar
                // and not as more
                // pane output, and the active window reads as the active window.
                "set-option",
                "-t",
                session,
                "status-style",
                "bg=colour252,fg=colour235",
                ";",
                // A window option has no session scope: the hook styles each window this session
                // gets, and
                // the immediate run covers the one it already has.
                "set-hook",
                "-t",
                session,
                "after-new-window",
                WINDOW_OPTIONS,
                ";",
                "set-hook",
                "-R",
                "-t",
                "=" + session + ":",
                "after-new-window",
                ";",
                // Warp does not grab Shift+Left/Right, unlike Ctrl+b.
                "bind-key",
                "-n",
                "S-Left",
                "previous-window",
                ";",
                "bind-key",
                "-n",
                "S-Right",
                "next-window"));
    // tmux stops a command list at its first error, so one bad option costs the rest.
    if (options.exitCode() != 0) {
      log.atWarn()
          .setMessage("tmux session options failed")
          .addKeyValue("session", session)
          .addKeyValue("cause", OneLine.clipped(options.stderr()))
          .log();
    }
  }

  /**
   * The viewer must NOT open inside a worktree: removing one reaps every process whose cwd is under
   * it, and a stable home dir is never a reap target.
   */
  private static final Path VIEWER_CWD = Path.of(System.getProperty("user.home"));

  private void ensureViewer(String session, String dedicatedTitle) {
    // A control client is no screen to look at.
    var clients =
        processRunner.run(
            null,
            TIMEOUT,
            tmux("list-clients", "-t", "=" + session, "-F", "#{client_control_mode}"));
    if (clients.exitCode() == 0 && clients.stdout().lines().anyMatch("0"::equals)) {
      return;
    }
    terminalDriver.openViewer(tmux("attach", "-t", session), dedicatedTitle, VIEWER_CWD);
  }

  private List<String> tmux(String... args) {
    return reads.argv(args);
  }
}

package dev.jagt.orchestrator.adapter.terminal;

import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * What every session host does the same: names its session, launches or revives the agent, and
 * opens a window in the order ensure session, replace, create, attach a viewer; only focus selects.
 * {@code S} is what a host's ensured session hands back, {@code W} how it addresses a created
 * window.
 */
@Slf4j
public abstract class AbstractSessionHost<S, W> implements SessionHost {

  protected final Object lock = new Object();
  protected final SessionEnvironment environment;
  private final AgentRuntime agentRuntime;

  protected AbstractSessionHost(AgentRuntime agentRuntime, SessionEnvironment environment) {
    this.agentRuntime = agentRuntime;
    this.environment = environment;
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
      LaunchMode mode,
      String model) {
    launch(
        session,
        dedicatedTitle,
        taskId,
        alias,
        worktreePath,
        model.isBlank()
            ? agentRuntime.launchCommand(worktreePath, mode)
            : agentRuntime.launchCommand(worktreePath, mode, model));
  }

  @Override
  public void reviveTaskWindow(
      String session,
      String dedicatedTitle,
      String taskId,
      @Nullable String alias,
      Path worktreePath) {
    launch(
        session,
        dedicatedTitle,
        taskId,
        alias,
        worktreePath,
        agentRuntime.reviveCommand(worktreePath));
  }

  private void launch(
      String session,
      String dedicatedTitle,
      String taskId,
      @Nullable String alias,
      Path worktreePath,
      String command) {
    agentRuntime.beforeLaunch(worktreePath);
    synchronized (lock) {
      S ensured = ensureSession(session);
      killTaskWindows(session, taskId);
      W window =
          newWindow(
              session, ensured, taskId, worktreePath, environment.prefix(worktreePath) + command);
      log.atInfo()
          .setMessage("task window opened")
          .addKeyValue("task", taskId)
          .addKeyValue("window", windowId(window))
          .log();
      if (alias != null && !alias.isBlank()) {
        label(session, window, alias);
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
      S ensured = ensureSession(session);
      killTaskWindows(session, name);
      newWindow(session, ensured, name, cwd, line);
      ensureViewer(session, dedicatedTitle);
    }
  }

  @Override
  public boolean focusTaskWindow(String session, String dedicatedTitle, String taskId) {
    synchronized (lock) {
      Optional<W> window = find(session, taskId);
      if (window.isEmpty()) {
        return false;
      }
      select(session, window.get());
      ensureViewer(session, dedicatedTitle);
      return true;
    }
  }

  /** Makes sure the session exists, with whatever options it needs. */
  protected abstract S ensureSession(String session);

  /** Creates a window named {@code name} running {@code command} in {@code cwd}, unshown. */
  protected abstract W newWindow(String session, S ensured, String name, Path cwd, String command);

  /** The window of that name, empty when there is none. */
  protected abstract Optional<W> find(String session, String taskId);

  /** Brings the window to whoever is attached; a failure throws. */
  protected abstract void select(String session, W window);

  /** Attaches a viewer unless a human is already looking. */
  protected abstract void ensureViewer(String session, String dedicatedTitle);

  /** Shows the alias beside the window's name, which stays the task's identity. */
  protected abstract void label(String session, W window, String alias);

  /** What a log line calls the window. */
  protected abstract String windowId(W window);
}

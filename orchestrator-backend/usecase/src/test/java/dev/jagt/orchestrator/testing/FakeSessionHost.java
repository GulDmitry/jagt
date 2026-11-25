package dev.jagt.orchestrator.testing;

import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class FakeSessionHost implements SessionHost {

  public record Typed(String taskId, String message) {}

  private final List<Typed> typed = new ArrayList<>();

  public List<Typed> typed() {
    return List.copyOf(typed);
  }

  @Override
  public String sessionName(@Nullable String configured) {
    return "fake";
  }

  @Override
  public void openTaskWindow(
      String session,
      String dedicatedTitle,
      String taskId,
      @Nullable String alias,
      Path worktreePath,
      LaunchMode mode,
      String model) {}

  @Override
  public void reviveTaskWindow(
      String session,
      String dedicatedTitle,
      String taskId,
      @Nullable String alias,
      Path worktreePath) {}

  @Override
  public void openCommandWindow(
      String session, String dedicatedTitle, String name, List<String> command) {}

  @Override
  public void openShellWindow(
      String session, String dedicatedTitle, String name, Path cwd, String command) {}

  @Override
  public boolean focusTaskWindow(String session, String dedicatedTitle, String taskId) {
    return true;
  }

  @Override
  public WindowState taskWindowState(String session, String taskId) {
    return WindowState.AGENT_RUNNING;
  }

  @Override
  public Optional<String> screenOf(String session, String taskId) {
    return Optional.of("");
  }

  @Override
  public Optional<String> exitTail(String session, String taskId) {
    return Optional.empty();
  }

  @Override
  public boolean nudgeTaskWindow(String session, String taskId, String message) {
    typed.add(new Typed(taskId, message));
    return true;
  }

  @Override
  public boolean submitTaskWindow(String session, String taskId) {
    return true;
  }

  @Override
  public boolean pressTaskWindow(String session, String taskId, Key key) {
    return true;
  }

  @Override
  public int killTaskWindows(String session, String taskId) {
    return 0;
  }

  @Override
  public long lastWindowActivityMillis(String session, String taskId) {
    return 0;
  }
}

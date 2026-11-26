package dev.jagt.orchestrator.adapter.terminal;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** What every session host does the same: names its session and launches or revives the agent. */
public abstract class AbstractSessionHost implements SessionHost {

  private final AgentRuntime agentRuntime;

  protected AbstractSessionHost(AgentRuntime agentRuntime) {
    this.agentRuntime = agentRuntime;
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
    taskWindow(session, dedicatedTitle, taskId, alias, worktreePath, command);
  }

  /** Replaces the task's window with one running {@code command}, and shows it to the human. */
  protected abstract void taskWindow(
      String session,
      String dedicatedTitle,
      String taskId,
      @Nullable String alias,
      Path worktreePath,
      String command);
}

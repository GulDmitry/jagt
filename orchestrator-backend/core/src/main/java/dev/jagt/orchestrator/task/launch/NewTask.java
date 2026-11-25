package dev.jagt.orchestrator.task.launch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What creating a task needs, as one value. {@code baseBranch} is the per-task OVERRIDE of the
 * project's configured base — the branch the worktree is cut from AND the branch its review request
 * targets; null means the project's own. {@code projectKeys} holds every repository the one session
 * works in, the agent's own FIRST.
 */
public record NewTask(
    String taskId,
    List<String> projectKeys,
    @Nullable String instructions,
    @Nullable String mode,
    @Nullable String branchStrategy,
    @Nullable String baseBranch,
    @Nullable String title,
    @Nullable String ticketUrl) {

  public NewTask {
    projectKeys = distinct(projectKeys);
  }

  public NewTask withProjectKeys(List<String> keys) {
    return new NewTask(
        taskId, keys, instructions, mode, branchStrategy, baseBranch, title, ticketUrl);
  }

  public @Nullable String projectKey() {
    return projectKeys.isEmpty() ? null : projectKeys.get(0);
  }

  private static List<String> distinct(@Nullable List<String> keys) {
    if (keys == null || keys.isEmpty()) {
      return List.of();
    }
    // Dropping a blank one silently would promote the next repository to be the session's own.
    if (keys.get(0) == null || keys.get(0).isBlank()) {
      throw new IllegalArgumentException("A task needs the project its session runs in");
    }
    // Named twice is one worktree, not two, and the same repository cannot be cut for one task
    // twice.
    Set<String> distinct = new LinkedHashSet<>();
    distinct.add(keys.get(0));
    keys.stream().skip(1).filter(key -> key != null && !key.isBlank()).forEach(distinct::add);
    return List.copyOf(distinct);
  }

  public static NewTaskBuilder builder(String taskId, @Nullable String projectKey) {
    NewTaskBuilder builder = new NewTaskBuilder(taskId);
    builder.projectKeys.add(projectKey);
    return builder;
  }

  /** A task that plans before it has a repository. */
  public static NewTaskBuilder unplacedBuilder(String taskId) {
    return new NewTaskBuilder(taskId);
  }

  public static class NewTaskBuilder {
    private final String taskId;
    private List<String> projectKeys = new ArrayList<>();
    private @Nullable String instructions;
    private @Nullable String mode;
    private @Nullable String branchStrategy;
    private @Nullable String baseBranch;
    private @Nullable String title;
    private @Nullable String ticketUrl;

    private NewTaskBuilder(String taskId) {
      this.taskId = taskId;
    }

    public NewTaskBuilder instructions(@Nullable String instructions) {
      this.instructions = instructions;
      return this;
    }

    public NewTaskBuilder mode(@Nullable String mode) {
      this.mode = mode;
      return this;
    }

    public NewTaskBuilder branchStrategy(@Nullable String branchStrategy) {
      this.branchStrategy = branchStrategy;
      return this;
    }

    public NewTaskBuilder baseBranch(@Nullable String baseBranch) {
      this.baseBranch = baseBranch;
      return this;
    }

    public NewTaskBuilder title(@Nullable String title) {
      this.title = title;
      return this;
    }

    public NewTaskBuilder ticketUrl(@Nullable String ticketUrl) {
      this.ticketUrl = ticketUrl;
      return this;
    }

    public NewTask build() {
      return new NewTask(
          taskId, projectKeys, instructions, mode, branchStrategy, baseBranch, title, ticketUrl);
    }

    /** Further repositories the same session works in; the session's own project stays first. */
    public NewTaskBuilder alsoIn(@Nullable List<String> projects) {
      if (projects != null) {
        projectKeys.addAll(projects);
      }
      return this;
    }
  }
}

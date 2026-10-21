package dev.jagt.orchestrator.task;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * What creating a task needs, as one value. {@code baseBranch} is the per-task OVERRIDE of the project's configured
 * base — the branch the worktree is cut from AND the branch its review request targets; null means the project's
 * own. {@code projectKeys} holds every repository the one session works in, the agent's own FIRST.
 */
public record NewTask(String taskId, List<String> projectKeys, @Nullable String instructions, @Nullable String mode,
                      @Nullable String branchStrategy, @Nullable String baseBranch, @Nullable String title,
                      @Nullable String ticketUrl) {

    public NewTask {
        projectKeys = projectKeys == null ? List.of() : List.copyOf(projectKeys);
    }

    public @Nullable String projectKey() {
        return projectKeys.isEmpty() ? null : projectKeys.get(0);
    }

    public static Builder builder(String taskId, @Nullable String projectKey) {
        return new Builder(taskId, projectKey);
    }

    public static final class Builder {
        private final String taskId;
        private final List<String> projectKeys = new ArrayList<>();
        private @Nullable String instructions;
        private @Nullable String mode;
        private @Nullable String branchStrategy;
        private @Nullable String baseBranch;
        private @Nullable String title;
        private @Nullable String ticketUrl;

        private Builder(String taskId, @Nullable String projectKey) {
            this.taskId = taskId;
            this.projectKeys.add(projectKey);
        }

        /** Further repositories the same session works in; the session's own project stays first. */
        public Builder alsoIn(@Nullable List<String> projects) {
            if (projects != null) {
                projects.stream().filter(p -> p != null && !p.isBlank()).forEach(projectKeys::add);
            }
            return this;
        }

        public Builder instructions(@Nullable String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder mode(@Nullable String mode) {
            this.mode = mode;
            return this;
        }

        public Builder branchStrategy(@Nullable String branchStrategy) {
            this.branchStrategy = branchStrategy;
            return this;
        }

        public Builder baseBranch(@Nullable String baseBranch) {
            this.baseBranch = baseBranch;
            return this;
        }

        public Builder title(@Nullable String title) {
            this.title = title;
            return this;
        }

        public Builder ticketUrl(@Nullable String ticketUrl) {
            this.ticketUrl = ticketUrl;
            return this;
        }

        public NewTask build() {
            // Dropping a blank one silently would promote the next repository to be the session's own.
            if (projectKeys.get(0) == null || projectKeys.get(0).isBlank()) {
                throw new IllegalArgumentException("A task needs the project its session runs in");
            }
            // Named twice is one worktree, not two, and the same repository cannot be cut for one task twice.
            LinkedHashSet<String> named = new LinkedHashSet<>(projectKeys);
            return new NewTask(taskId, new ArrayList<>(named), instructions, mode, branchStrategy, baseBranch,
                    title, ticketUrl);
        }
    }
}

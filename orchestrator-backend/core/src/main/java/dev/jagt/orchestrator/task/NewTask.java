package dev.jagt.orchestrator.task;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * What creating a task needs, as one value. {@code baseBranch} is the per-task OVERRIDE of the project's configured
 * base — the branch the worktree is cut from AND the branch its review request targets; null means the project's
 * own. {@code projectKeys} holds every repository the one session works in, the agent's own FIRST.
 */
@Builder(buildMethodName = "assemble")
public record NewTask(String taskId, List<String> projectKeys, @Nullable String instructions, @Nullable String mode,
                      @Nullable String branchStrategy, @Nullable String baseBranch, @Nullable String title,
                      @Nullable String ticketUrl) {

    public NewTask {
        projectKeys = projectKeys == null ? List.of() : List.copyOf(projectKeys);
    }

    public @Nullable String projectKey() {
        return projectKeys.isEmpty() ? null : projectKeys.get(0);
    }

    public static NewTaskBuilder builder(String taskId, @Nullable String projectKey) {
        NewTaskBuilder builder = new NewTaskBuilder().taskId(taskId);
        builder.projectKeys.add(projectKey);
        return builder;
    }

    @SuppressWarnings("NullAway.Init")
    public static class NewTaskBuilder {
        private List<String> projectKeys = new ArrayList<>();

        /** Further repositories the same session works in; the session's own project stays first. */
        public NewTaskBuilder alsoIn(@Nullable List<String> projects) {
            if (projects != null) {
                projects.stream().filter(p -> p != null && !p.isBlank()).forEach(projectKeys::add);
            }
            return this;
        }

        public NewTask build() {
            // Dropping a blank one silently would promote the next repository to be the session's own.
            if (projectKeys.get(0) == null || projectKeys.get(0).isBlank()) {
                throw new IllegalArgumentException("A task needs the project its session runs in");
            }
            // Named twice is one worktree, not two, and the same repository cannot be cut for one task twice.
            projectKeys = new ArrayList<>(new LinkedHashSet<>(projectKeys));
            return assemble();
        }
    }
}

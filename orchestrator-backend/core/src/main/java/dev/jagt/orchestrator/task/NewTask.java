package dev.jagt.orchestrator.task;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What creating a task needs, as one value — the chain that carries it used to pass eight positional Strings of
 * which a typical call filled three, so two swapped nulls were indistinguishable at every hop.
 *
 * <p>{@code baseBranch} is the per-task OVERRIDE of the project's configured base: the branch the worktree is
 * cut from AND the branch its review request targets. Null (the normal case) means "the project's baseBranch",
 * and it stays null in state.json so a later config change still reaches those tasks.
 *
 * @param projectKeys every repository the one session works in, the agent's own FIRST — it runs there and edits
 *                    the others in place. Ordinary work names one.
 */
public record NewTask(String taskId, List<String> projectKeys, String instructions, String mode,
                      String branchStrategy, String baseBranch, String title, String ticketUrl) {

    public NewTask {
        projectKeys = projectKeys == null ? List.of() : List.copyOf(projectKeys);
    }

    /** Where the agent's session runs, which is also the repo every single-repo accessor answers for. */
    public String projectKey() {
        return projectKeys.isEmpty() ? null : projectKeys.get(0);
    }

    /** taskId + the session's project are the only two a task cannot be created without. */
    public static Builder builder(String taskId, String projectKey) {
        return new Builder(taskId, projectKey);
    }

    public static final class Builder {
        private final String taskId;
        private final List<String> projectKeys = new ArrayList<>();
        private String instructions;
        private String mode;
        private String branchStrategy;
        private String baseBranch;
        private String title;
        private String ticketUrl;

        private Builder(String taskId, String projectKey) {
            this.taskId = taskId;
            this.projectKeys.add(projectKey);
        }

        /** The further repositories this one session also works in; the session's own project stays first. */
        public Builder alsoIn(List<String> projects) {
            if (projects != null) {
                projects.stream().filter(p -> p != null && !p.isBlank()).forEach(projectKeys::add);
            }
            return this;
        }

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder branchStrategy(String branchStrategy) {
            this.branchStrategy = branchStrategy;
            return this;
        }

        public Builder baseBranch(String baseBranch) {
            this.baseBranch = baseBranch;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder ticketUrl(String ticketUrl) {
            this.ticketUrl = ticketUrl;
            return this;
        }

        public NewTask build() {
            // Dropping a blank one silently would promote the next repository to be the one the agent's session
            // runs in — a different task than the caller asked for, and nothing downstream could tell.
            if (projectKeys.get(0) == null || projectKeys.get(0).isBlank()) {
                throw new IllegalArgumentException("A task needs the project its session runs in");
            }
            // Named twice is one worktree, not two, and the same repository cannot be cut for one task twice.
            LinkedHashSet<String> named = projectKeys.stream()
                    .filter(key -> key != null && !key.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new NewTask(taskId, new ArrayList<>(named), instructions, mode, branchStrategy, baseBranch,
                    title, ticketUrl);
        }
    }
}

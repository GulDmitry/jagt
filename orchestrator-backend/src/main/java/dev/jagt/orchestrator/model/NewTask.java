package dev.jagt.orchestrator.model;

/**
 * What creating a task needs, as one value. The chain that carries it (console/board -> {@code TaskLauncher}
 * -> {@code OrchestratorTools} -> {@code TaskProvisioning}) passed eight positional Strings of which a typical
 * call filled three, so two swapped nulls were indistinguishable at every hop.
 *
 * <p>{@code baseBranch} is the per-task OVERRIDE of the project's configured base: the branch the worktree is
 * cut from AND the branch its review request targets. Null (the normal case) means "the project's baseBranch",
 * and it stays null in state.json so a later config change still reaches those tasks.
 */
public record NewTask(String taskId, String projectKey, String instructions, String mode,
                      String branchStrategy, String baseBranch, String title, String ticketUrl) {

    /** taskId + projectKey are the only two a task cannot be created without; the rest are layered on. */
    public static Builder builder(String taskId, String projectKey) {
        return new Builder(taskId, projectKey);
    }

    public static final class Builder {
        private final String taskId;
        private final String projectKey;
        private String instructions;
        private String mode;
        private String branchStrategy;
        private String baseBranch;
        private String title;
        private String ticketUrl;

        private Builder(String taskId, String projectKey) {
            this.taskId = taskId;
            this.projectKey = projectKey;
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
            return new NewTask(taskId, projectKey, instructions, mode, branchStrategy, baseBranch, title,
                    ticketUrl);
        }
    }
}

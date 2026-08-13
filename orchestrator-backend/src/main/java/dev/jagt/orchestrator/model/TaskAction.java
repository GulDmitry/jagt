package dev.jagt.orchestrator.model;

/**
 * Something a human can do to one task. Named here rather than spelled out in each front-end, so the TUI, the
 * web UI and the HTTP API offer exactly the same set — and so an action that is not legal for a task cannot be
 * offered at all (see {@link Move}).
 *
 * <p>{@code id} is the wire name (URL segment, HTML data attribute) and the CLI verb; keeping them the same
 * string means a button and a typed command cannot drift apart.
 */
public enum TaskAction {

    FOCUS("focus", "Focus", "jump to the agent's terminal window"),
    IDE("ide", "Open IDE", "open the worktree as a project — Git → Local Changes is the live diff"),
    DIFF("diff", "Diff", "static snapshot of the change vs the deploy branch"),
    SHIP("ship", "Ship", "approve: commit, push, open or update the review request"),
    SWEEP("review", "Check review", "pull the pipeline + unresolved comments and relay them to the agent"),
    DEPLOY("deploy", "Deploy", "merge the task branch into the deploy branch and push"),
    RESPAWN("respawn", "Respawn", "restart a dead agent session"),
    DONE("done", "Done", "close the task: session, worktree and state (the branch is kept)");

    private final String id;
    private final String label;
    private final String hint;

    TaskAction(String id, String label, String hint) {
        this.id = id;
        this.label = label;
        this.hint = hint;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String hint() {
        return hint;
    }

    /** The action for a wire id, or empty — an unknown id from a URL must never resolve to something else. */
    public static java.util.Optional<TaskAction> byId(String id) {
        return java.util.Arrays.stream(values()).filter(action -> action.id.equals(id)).findFirst();
    }
}

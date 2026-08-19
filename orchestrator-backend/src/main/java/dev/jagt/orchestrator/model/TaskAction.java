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

    SHIP(Group.FLOW, "ship", "Ship", "approve: commit, push, open or update the review request"),
    SWEEP(Group.FLOW, "sweep", "Check review",
            "pull the checks + unresolved comments and relay them to the agent"),
    DEPLOY(Group.FLOW, "deploy", "Deploy", "merge the task branch into the deploy branch and push"),
    REVERT(Group.FLOW, "revert", "Revert",
            "undo this task's deploy: revert its merge commit on the deploy branch and push"),
    DONE(Group.FLOW, "done", "Done", "close the task: session, worktree and state (the branch is kept)"),
    FOCUS(Group.TOOL, "focus", "Focus", "jump to the agent's terminal window"),
    IDE(Group.TOOL, "ide", "Open IDE", "open the worktree as a project — Git → Local Changes is the live diff",
            "ide <ticket> [diff]"),
    DIFF(Group.TOOL, "diff", "Diff", "static snapshot of the change vs the deploy branch"),
    RESPAWN(Group.TOOL, "respawn", "Restart agent",
            "start a new agent session in the same worktree — it re-reads its brief");

    /**
     * Which half of a card an action belongs to. FLOW moves the task along its life — closing it included; TOOL
     * only looks at what is already there, or starts the same agent again. Declared once because both surfaces
     * order and separate them the same way, and a card that mixes "push to a shared branch" in among "open the
     * diff" makes the human read every button before pressing one.
     */
    public enum Group {

        FLOW("flow"), TOOL("tool");

        private final String id;

        Group(String id) {
            this.id = id;
        }

        /** The wire name, so a front-end can group without knowing the enum. */
        public String id() {
            return id;
        }
    }

    /** Spellings a verb was renamed from: accepted wherever one is typed, advertised nowhere. */
    private static final java.util.Map<String, TaskAction> RENAMED = java.util.Map.of("review", SWEEP);

    private final Group group;
    private final String id;
    private final String label;
    private final String hint;
    private final String usage;

    TaskAction(Group group, String id, String label, String hint) {
        this(group, id, label, hint, id + " <ticket>");
    }

    TaskAction(Group group, String id, String label, String hint, String usage) {
        this.group = group;
        this.id = id;
        this.label = label;
        this.hint = hint;
        this.usage = usage;
    }

    public Group group() {
        return group;
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

    /** What a human types, for the command reference: the verb and its argument, not prose. */
    public String usage() {
        return usage;
    }

    /** The action for a wire id, or empty — an unknown id from a URL must never resolve to something else. */
    public static java.util.Optional<TaskAction> byId(String id) {
        return java.util.Arrays.stream(values()).filter(action -> action.id.equals(id)).findFirst();
    }

    /**
     * The action a retired spelling still names, or empty — never a current id, so a verb set stays closed.
     * Case and padding are normalized HERE, because every surface that accepts the old word comes through this
     * method and one of them must not be stricter than the next.
     */
    public static java.util.Optional<TaskAction> byRetiredVerb(String verb) {
        return verb == null ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(RENAMED.get(verb.strip().toLowerCase(java.util.Locale.ROOT)));
    }

    /** What this action also answers to, for a surface that must accept what it does not offer. */
    public java.util.List<String> retiredVerbs() {
        return RENAMED.entrySet().stream().filter(renamed -> renamed.getValue() == this)
                .map(java.util.Map.Entry::getKey).sorted().toList();
    }
}

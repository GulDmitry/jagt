package dev.jagt.orchestrator.flow;

/**
 * Something a human can do to one task, named once so every surface offers exactly the same set.
 *
 * <p>{@code id} is the wire name (URL segment, HTML data attribute) AND the CLI verb: one string, so a button and
 * a typed command cannot drift apart.
 */
public enum TaskAction {

    SHIP(Group.FLOW, "ship", "Ship", "commit, push, open or update the review request"),
    SWEEP(Group.FLOW, "sweep", "Check review",
            "read the checks and unresolved comments, relay them to the agent"),
    DEPLOY(Group.FLOW, "deploy", "Deploy", "merge the task branch into the deploy branch and push"),
    REVERT(Group.FLOW, "revert", "Revert",
            "revert the last deploy's merge commit and push; earlier deploys stay live"),
    DONE(Group.FLOW, "done", "Done", "kill the session, delete the worktree, drop the task; the branch is kept"),
    FOCUS(Group.TOOL, "focus", "Focus", "open the agent's terminal window"),
    IDE(Group.TOOL, "ide", "Open IDE", "open the worktree in the IDE; Local Changes holds the uncommitted diff",
            "ide <ticket> [diff]"),
    DIFF(Group.TOOL, "diff", "Diff", "show the diff against the deploy branch"),
    RESPAWN(Group.TOOL, "respawn", "Restart agent",
            "start a new agent session in the same worktree");

    /**
     * Which half of a card an action belongs to: FLOW moves the task along its life, closing it included; TOOL
     * only looks at what is already there, or starts the same agent again.
     */
    public enum Group {

        FLOW("flow"), TOOL("tool");

        private final String id;

        Group(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    /**
     * Actions that only LOOK at a task. What a click writes is not the same question as which half of the card it
     * is rendered on: `respawn` sits with the tools and kills a running session, so a surface that locks a card
     * while a move of its own is in flight has to ask this rather than the group.
     */
    private static final java.util.Set<TaskAction> READ_ONLY = java.util.EnumSet.of(FOCUS, IDE, DIFF);

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

    /** Whether this action changes nothing, so nothing else being in flight is a reason to refuse it. */
    public boolean readOnly() {
        return READ_ONLY.contains(this);
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

    /** What a human types: the verb and its argument, not prose. */
    public String usage() {
        return usage;
    }

    /** The action for a wire id, or empty — an unknown id from a URL must never resolve to something else. */
    public static java.util.Optional<TaskAction> byId(String id) {
        return java.util.Arrays.stream(values()).filter(action -> action.id.equals(id)).findFirst();
    }

    /**
     * The action a retired spelling still names, or empty — never a current id, so a verb set stays closed. Case
     * and padding are normalized HERE, so no surface that accepts the old word is stricter than the next.
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

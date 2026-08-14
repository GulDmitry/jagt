package dev.jagt.orchestrator.service;

/**
 * The command grammar, in one place, because BOTH surfaces show it: the console prints it for `help`, the board
 * serves it behind its Help button.
 */
public final class CommandReference {

    /**
     * One verb, as the board's palette needs it: what to type, what it does, and whether it takes a task. The
     * palette uses this to COMPLETE and VALIDATE what a human types — and, when the line parses, to run it
     * deterministically instead of paying a model to map it (tier 1 before tier 2).
     */
    public record Verb(String id, String hint, boolean takesTask) {
    }

    /** Most-used first; a verb missing here sorts to the end rather than being dropped. */
    private static final java.util.List<String> BY_USE = java.util.List.of(
            "review", "ship", "do", "ide", "diff", "focus", "resume", "deploy", "stats", "respawn",
            "revert", "done", "help");

    private CommandReference() {
    }

    /** Every verb the console accepts, including the ones that are not per-task actions. */
    public static java.util.List<Verb> verbs() {
        java.util.List<Verb> verbs = new java.util.ArrayList<>();
        for (dev.jagt.orchestrator.model.TaskAction action : dev.jagt.orchestrator.model.TaskAction.values()) {
            verbs.add(new Verb(action.id(), action.hint(), true));
        }
        verbs.add(new Verb("do", "start a task from a ticket key or URL", false));
        verbs.add(new Verb("resume", "take over an existing review request (its URL)", false));
        verbs.add(new Verb("stats", "token spend of jagt's own model calls", false));
        verbs.add(new Verb("help", "this command reference", false));
        verbs.sort(java.util.Comparator.comparingInt(verb -> {
            int rank = BY_USE.indexOf(verb.id());
            return rank < 0 ? BY_USE.size() : rank;
        }));
        return java.util.List.copyOf(verbs);
    }

    public static String text() {
        return String.join("\n",
                "commands (task = ticket id or alias):",
                "  status                       show the dashboard",
                "  stats                        token spend of jagt's own model calls, per task",
                "  do <ticket> [project] [plan] spin up a sub-agent in a worktree",
                "    … [from <branch>]          cut the worktree from <branch> and target its MR at it",
                "  resume <mr-url>              reopened MR: resume its branch + link it -> CI_POLLING",
                "  focus <ticket>               jump to the agent's window (talk to it there)",
                "  ship <ticket>                approve: commit (pattern title), push, open/update the MR",
                "  review <ticket>              pull the MR's pipeline + comments, relay them to the agent",
                "  ide <ticket> [diff]          open worktree project (live Git diff); `diff` = static snapshot vs base",
                "                               on DEPLOY_CONFLICT it opens the DEPLOY worktree to resolve in",
                "  deploy <ticket>              merge task branch into deployBranch + push",
                "  revert <ticket>              undo that deploy: revert its merge on deployBranch + push",
                "  respawn <ticket>             restart a dead agent session",
                "  done <ticket>                full cleanup (window, worktree, state; branch kept)",
                "  help | quit                  this reference | detach (agents keep running)",
                "",
                "anything else is free text: a model maps it to ONE of the above and jagt runs it through the",
                "same gate a button uses (the board's Ask / \u2318K).");
    }
}

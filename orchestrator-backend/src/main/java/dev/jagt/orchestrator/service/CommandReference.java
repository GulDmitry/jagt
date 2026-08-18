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
    public record Verb(String id, String hint, boolean takesTask, java.util.List<String> aliases) {
    }

    /** Most-used first; a verb missing here sorts to the end rather than being dropped. */
    private static final java.util.List<String> BY_USE = java.util.List.of(
            "sweep", "ship", "do", "ide", "diff", "focus", "resume", "deploy", "stats", "respawn",
            "revert", "done", "activity", "help");

    private CommandReference() {
    }

    /** Every verb the console accepts, including the ones that are not per-task actions. */
    public static java.util.List<Verb> verbs() {
        java.util.List<Verb> verbs = new java.util.ArrayList<>();
        for (dev.jagt.orchestrator.model.TaskAction action : dev.jagt.orchestrator.model.TaskAction.values()) {
            verbs.add(new Verb(action.id(), action.hint(), true, action.retiredVerbs()));
        }
        verbs.add(new Verb("do", "start a task from a ticket key or URL", false, java.util.List.of()));
        verbs.add(new Verb("resume", "take over an existing review request (its URL)", false,
                java.util.List.of()));
        verbs.add(new Verb("stats", "what jagt's own model calls cost, and where each task's time went", false,
                java.util.List.of()));
        verbs.add(new Verb("activity", "what jagt did on its own, newest first", false,
                java.util.List.of()));
        verbs.add(new Verb("help", "this command reference", false, java.util.List.of()));
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
                "  stats                        model spend per task, and where each task's time went",
                "  activity                     what jagt did unattended (polls, relays, agent reports)",
                "  do <ticket> [project] [plan] spin up a sub-agent in a worktree",
                "    … [proj1,proj2]            one session, a worktree in EACH: work that spans repositories",
                "    … [from <branch>]          cut the worktree from <branch> and target its request at it",
                "  resume <request-url>         reopened request: resume its branch + link it -> CI_POLLING",
                "  focus <ticket>               jump to the agent's window (talk to it there)",
                "  ship <ticket>                approve: commit (pattern title), push, open/update the request",
                "  sweep <ticket>               pull the request's checks + comments, relay them to the agent",
                "  ide <ticket> [diff]          open worktree project (live Git diff)",
                "  diff <ticket>                static snapshot vs the base branch",
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

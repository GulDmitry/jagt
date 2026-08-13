package dev.jagt.orchestrator.service;

/**
 * The command grammar, in one place, because BOTH surfaces show it: the console prints it for `help` and the
 * board serves it behind its Help button. It lived inside {@code MasterShell} while the board had no help at
 * all — which is how a capability ends up existing in one surface only.
 */
public final class CommandReference {

    private CommandReference() {
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
                "  prune [all]                  list LOCAL branches merged into deployBranch; `all` deletes them",
                "  help | quit                  this reference | detach (agents keep running)",
                "",
                "anything else is free text: a model maps it to ONE of the above and jagt runs it through the",
                "same gate a button uses (the board's Ask / \u2318K).");
    }
}

package dev.jagt.orchestrator.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LsofWorktreeProcessesTest {

    @Test
    void reapSparesTheTmuxViewerButTakesAgentDaemonsUnderTheWorktree() {
        // `done`/`remove` reap every process whose cwd sits under the removed worktree. The viewer window
        // runs `tmux attach` as its foreground program, so its cwd is that worktree — but reaping it kill-9s
        // the viewer, closing the whole terminal window while every OTHER agent keeps running in the shared
        // tmux server. tmux must be spared; the agent's own leftover daemons (jdtls, node) must not.
        String target = "/Users/x/www/wt";
        String lsof = String.join("\n",
                "p100", "cjava", "fcwd", "n" + target,               // jdtls rooted in the worktree
                "p200", "cnode", "fcwd", "n" + target + "/app",      // an MCP daemon deeper in the worktree
                "p300", "ctmux", "fcwd", "n" + target,               // the viewer's `tmux attach`
                "p400", "cjava", "fcwd", "n/Users/x/www/other");     // jdtls of a DIFFERENT worktree

        assertThat(LsofWorktreeProcesses.reapable(lsof, target)).extracting(LsofWorktreeProcesses.Reapable::pid)
                .containsExactly("100", "200");
    }
}

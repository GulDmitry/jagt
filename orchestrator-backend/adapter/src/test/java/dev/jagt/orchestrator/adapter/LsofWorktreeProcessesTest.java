package dev.jagt.orchestrator.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LsofWorktreeProcessesTest {

    /**
     * The viewer window runs {@code tmux attach} as its foreground program, so its cwd is the worktree being
     * removed — reaping it kill-9s the viewer and closes the window every OTHER agent is watched in.
     */
    @Test
    void reapSparesTheTmuxViewerButTakesAgentDaemonsUnderTheWorktree() {
        String lsof = String.join("\n",
                "p100", "cjava", "fcwd", "n/Users/x/www/wt",
                "p200", "cnode", "fcwd", "n/Users/x/www/wt/app",
                "p300", "ctmux", "fcwd", "n/Users/x/www/wt",
                "p400", "cjava", "fcwd", "n/Users/x/www/other");

        assertThat(LsofWorktreeProcesses.reapable(lsof, "/Users/x/www/wt"))
                .extracting(LsofWorktreeProcesses.Reapable::pid)
                .containsExactly("100", "200");
    }
}

package dev.jagt.orchestrator.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LsofWorktreeProcessesTest {

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

package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StandShellTest {

    @Test
    void aMacHoldsTheWakeLockWithCaffeinateForTheStandJvm() {
        assertThat(StandShell.awakeCommand("Mac OS X", 4242, name -> name.equals("caffeinate")))
                .contains(List.of("caffeinate", "-i", "-s", "-w", "4242"));
    }

    @Test
    void aLinuxWithSystemdInhibitBlocksSleepForTheStandJvm() {
        assertThat(StandShell.awakeCommand("Linux", 4242, name -> name.equals("systemd-inhibit")).orElseThrow())
                .startsWith("systemd-inhibit").contains("--pid=4242");
    }

    @Test
    void aHostWithoutTheToolHoldsNoWakeLock() {
        assertThat(StandShell.awakeCommand("Linux", 4242, name -> false)).isEmpty();
    }
}

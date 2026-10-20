package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeTest {

    @Test
    void writesACommandAsAPlanWouldWriteItWithItsExpectedExit() {
        Probe probe = Probe.specified(Probe.Kind.COMMAND, List.of("make", "check"), 2);

        assertThat(probe.toString()).isEqualTo("command exit=2 make check");
    }

    @Test
    void leavesTheExitOutWhenZeroIsExpected() {
        Probe probe = Probe.specified(Probe.Kind.REF, List.of("origin/main"), 0);

        assertThat(probe.toString()).isEqualTo("ref origin/main");
    }

    @Test
    void saysNotRunBeforeAProbeRan() {
        Probe probe = Probe.specified(Probe.Kind.ARTIFACT, List.of("docs/out.md"), 0);

        assertThat(probe.result()).isEqualTo("artifact docs/out.md — not run");
        assertThat(probe.failed()).isFalse();
    }

    @Test
    void reportsAPassedProbeWithoutAnExcerptAsOneLine() {
        Probe probe = Probe.specified(Probe.Kind.ARTIFACT, List.of("docs/out.md"), 0).probed(true, "  ");

        assertThat(probe.result()).isEqualTo("artifact docs/out.md — passed");
        assertThat(probe.failed()).isFalse();
    }

    @Test
    void reportsAFailedProbeWithWhatItPrinted() {
        Probe probe = Probe.specified(Probe.Kind.COMMAND, List.of("make", "check"), 0).probed(false, "2 failed");

        assertThat(probe.result()).isEqualTo("command make check — FAILED\n2 failed");
        assertThat(probe.failed()).isTrue();
    }
}

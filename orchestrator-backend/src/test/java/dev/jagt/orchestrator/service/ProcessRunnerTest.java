package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessRunnerTest {

    @Test
    void launchesTheAppOutsideTheProcessGroupTheTerminalSendsCtrlCTo() throws Exception {
        Process launched = new ProcessRunner().runDetached(null, List.of("sleep", "5"));

        assertThat(processGroupOf(launched.pid())).isNotEqualTo(processGroupOf(ProcessHandle.current().pid()));
        launched.destroyForcibly();
    }

    @Test
    void leavesTheAppKillableByPidSoTheWrapperIsNotWhatSurvives() throws Exception {
        Process launched = new ProcessRunner().runDetached(null, List.of("sleep", "5"));

        assertThat(commandOf(launched.pid())).isEqualTo("sleep");
        launched.destroyForcibly();
    }

    @Test
    void leavesTheAppInterruptibleByItsOwnTooling() throws Exception {
        Process launched = new ProcessRunner().runDetached(null, List.of("sleep", "5"));

        Process kill = new ProcessBuilder("kill", "-INT", String.valueOf(launched.pid())).start();

        assertThat(kill.waitFor()).isZero();
        assertThat(launched.waitFor(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void namesTheLaunchThatCouldNotStartInsteadOfAnsweringWithAProcess() {
        assertThatThrownBy(() -> new ProcessRunner().runDetached(null, List.of("jagt-no-such-binary")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jagt-no-such-binary");
    }

    @Test
    void refusesAnEmptyCommandInsteadOfLaunchingNothing() {
        assertThatThrownBy(() -> new ProcessRunner().runDetached(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runDetachedReturnsWithoutWaitingForTheProcess() {
        ProcessRunner runner = new ProcessRunner();

        long start = System.currentTimeMillis();
        runner.runDetached(null, List.of("sleep", "3"));
        long elapsedMillis = System.currentTimeMillis() - start;

        assertThat(elapsedMillis).isLessThan(2_000);
    }

    private static String processGroupOf(long pid) throws Exception {
        return firstLineOf(new ProcessBuilder("ps", "-o", "pgid=", "-p", String.valueOf(pid)));
    }

    private static String commandOf(long pid) throws Exception {
        return firstLineOf(new ProcessBuilder("ps", "-o", "comm=", "-p", String.valueOf(pid)));
    }

    private static String firstLineOf(ProcessBuilder query) throws Exception {
        Process process = query.redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes()).strip();
        process.waitFor();
        return output;
    }
}

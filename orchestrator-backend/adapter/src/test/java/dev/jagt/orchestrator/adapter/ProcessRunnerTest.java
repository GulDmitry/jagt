package dev.jagt.orchestrator.adapter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Alone: it reads the global process table and attaches to the shared logger, and `LoggerFactory` answers with
 * a substitute to any thread arriving while another is still binding the backend.
 */
@Isolated
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

    @ParameterizedTest
    @CsvSource({"143, on SIGTERM (143)", "130, on SIGINT (130)", "0, with exit 0", "128, with exit 128",
            "1, with exit 1"})
    void namesTheSignalAKilledLaunchDiedOnAndLeavesEveryOtherEndACode(int exitValue, String expected) {
        assertThat(ProcessRunner.endedBy(exitValue)).isEqualTo(expected);
    }

    @Test
    void recordsHowALaunchEndedSoADeathNobodyAskedForCanBeAttributed() throws Exception {
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger runnerLog = (Logger) LoggerFactory.getLogger(ProcessRunner.class);
        runnerLog.addAppender(log);
        Process launched = new ProcessRunner().runDetached(null, List.of("sleep", "30"));

        new ProcessBuilder("kill", "-TERM", String.valueOf(launched.pid())).start().waitFor();

        // The record is written by ITS OWN stage of the same future, which does not run before the test's.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(List.copyOf(log.list)).filteredOn(event -> "process ended".equals(event.getMessage()))
                        .flatExtracting(ILoggingEvent::getKeyValuePairs)
                        .extracting(pair -> pair.key + "=" + pair.value)
                        .contains("pid=" + launched.pid(), "exit=on SIGTERM (143)"));
        runnerLog.detachAppender(log);
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
    void handsTheAppBackWhileItIsStillRunningSoNothingWaitsOnTheEditor() {
        long start = System.currentTimeMillis();
        Process launched = new ProcessRunner().runDetached(null, List.of("sleep", "3"));
        long elapsedMillis = System.currentTimeMillis() - start;

        assertThat(elapsedMillis).isLessThan(2_000);
        launched.destroyForcibly();
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

package dev.jagt.orchestrator.adapter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

@Isolated
class ProcessRunnerTest {

    @Test
    void launchesTheAppOutsideTheProcessGroupTheTerminalSendsCtrlCTo() {
        ProcessRunner runner = new ProcessRunner();
        Process launched = runner.runDetached(null, List.of("sleep", "5"));

        try {
            String launchedGroup = runner.run(null, Duration.ofSeconds(5),
                    List.of("ps", "-o", "pgid=", "-p", String.valueOf(launched.pid()))).stdout().strip();
            String ownGroup = runner.run(null, Duration.ofSeconds(5),
                    List.of("ps", "-o", "pgid=", "-p", String.valueOf(ProcessHandle.current().pid()))).stdout().strip();

            assertThat(launchedGroup).isNotEqualTo(ownGroup);
        } finally {
            launched.destroyForcibly();
        }
    }

    @Test
    void leavesTheAppKillableByPidSoTheWrapperIsNotWhatSurvives() {
        ProcessRunner runner = new ProcessRunner();
        Process launched = runner.runDetached(null, List.of("sleep", "5"));

        try {
            String command = runner.run(null, Duration.ofSeconds(5),
                    List.of("ps", "-o", "comm=", "-p", String.valueOf(launched.pid()))).stdout().strip();

            assertThat(command).isEqualTo("sleep");
        } finally {
            launched.destroyForcibly();
        }
    }

    @Test
    void killsWhatATimedOutCommandSpawnedSoNoChildOutlivesIt(@TempDir Path dir) throws Exception {
        Path childPid = dir.resolve("pid");

        assertThatThrownBy(() -> new ProcessRunner().run(null, Duration.ofMillis(500),
                List.of("sh", "-c", "sleep 30 & echo $! > " + childPid + "; wait")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out");

        long child = Long.parseLong(Files.readString(childPid).strip());
        await().atMost(Duration.ofSeconds(5))
                .until(() -> ProcessHandle.of(child).filter(ProcessHandle::isAlive).isEmpty());
    }

    @Test
    void killsTheCommandWhenTheCallerWaitingOnItIsInterrupted(@TempDir Path dir) throws Exception {
        Path pid = dir.resolve("pid");
        Thread caller = Thread.startVirtualThread(() -> catchThrowable(() -> new ProcessRunner().run(null,
                Duration.ofSeconds(30), List.of("sh", "-c", "echo $$ > " + pid + "; exec sleep 30"))));
        await().ignoreExceptions().atMost(Duration.ofSeconds(5)).until(() -> Files.readString(pid).endsWith("\n"));
        long command = Long.parseLong(Files.readString(pid).strip());

        caller.interrupt();

        await().atMost(Duration.ofSeconds(5))
                .until(() -> ProcessHandle.of(command).filter(ProcessHandle::isAlive).isEmpty());
    }

    @Test
    void killsTheCommandsStillRunningWhenJagtStops(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
        Path pid = dir.resolve("pid");
        Thread.startVirtualThread(() -> catchThrowable(() -> runner.run(null, Duration.ofSeconds(30),
                List.of("sh", "-c", "echo $$ > " + pid + "; exec sleep 30"))));
        await().ignoreExceptions().atMost(Duration.ofSeconds(5)).until(() -> Files.readString(pid).endsWith("\n"));
        long command = Long.parseLong(Files.readString(pid).strip());

        runner.destroyRunning();

        await().atMost(Duration.ofSeconds(5))
                .until(() -> ProcessHandle.of(command).filter(ProcessHandle::isAlive).isEmpty());
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
    @ResourceLock(Resources.GLOBAL)
    void recordsHowALaunchEndedSoADeathNobodyAskedForCanBeAttributed() throws Exception {
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger runnerLog = (Logger) LoggerFactory.getLogger(ProcessRunner.class);
        runnerLog.addAppender(log);
        try {
            Process launched = new ProcessRunner().runDetached(null, List.of("sleep", "30"));

            new ProcessBuilder("kill", "-TERM", String.valueOf(launched.pid())).start().waitFor();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(List.copyOf(log.list)).filteredOn(event -> "process ended".equals(event.getMessage()))
                            .flatExtracting(ILoggingEvent::getKeyValuePairs)
                            .extracting(pair -> pair.key + "=" + pair.value)
                            .contains("pid=" + launched.pid(), "exit=on SIGTERM (143)"));
        } finally {
            runnerLog.detachAppender(log);
        }
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
        Process launched = new ProcessRunner().runDetached(null, List.of("sleep", "30"));

        try {
            assertThat(launched.isAlive()).isTrue();
        } finally {
            launched.destroyForcibly();
        }
    }
}

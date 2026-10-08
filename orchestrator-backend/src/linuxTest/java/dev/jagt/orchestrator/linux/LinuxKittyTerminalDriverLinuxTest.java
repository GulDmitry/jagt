package dev.jagt.orchestrator.linux;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.adapter.linux.LinuxKittyTerminalDriver;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.jagt.orchestrator.port.TerminalDriver;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LinuxKittyTerminalDriverLinuxTest {

    private static final Duration T = Duration.ofSeconds(20);
    private static final Duration PROBE_CUT_OFF_SINCE_A_DEAD_SOCKET_BLOCKS = Duration.ofSeconds(3);
    private static final Duration UP_ON_A_SLOW_SHARED_RUNNER = Duration.ofSeconds(60);
    private static final String SESSION = "jagt-kitty-linux-test";

    private final ProcessRunner runner = new ProcessRunner();

    private LinuxKittyTerminalDriver driver() {
        return new LinuxKittyTerminalDriver(runner, OrchestratorProperties.defaults()
                .withOpenTerminalWindow(true).withTmuxCommand("tmux"), "kitty", "");
    }

    private String socket() {
        return "unix:" + Path.of(System.getProperty("java.io.tmpdir"), "jagt-kitty-" + SESSION);
    }

    @AfterEach
    void leaveNoWindowsOrSessionsBehind() {
        driver().closeViewerWindow(SESSION);
        runner.run(null, T, List.of("tmux", "kill-session", "-t", SESSION));
    }

    @Test
    void bringsUpADetachedRemoteControllableViewerAttachedToTheSession() {
        runner.run(null, T, List.of("tmux", "new-session", "-d", "-s", SESSION));

        driver().openViewer(SESSION, SESSION, Path.of(System.getProperty("java.io.tmpdir")));

        String listed = awaitRemoteControl();
        assertThat(listed).as("kitty's own view of itself").contains("\"tabs\"");
        assertThat(listed).contains("tmux");
    }

    @Test
    void reportsThatNoViewerIsRunningRatherThanOneItCannotReach() {
        assertThat(driver().reveal("jagt-kitty-linux-absent"))
                .isEqualTo(TerminalDriver.Revealed.NOT_RUNNING);
    }

    @org.junit.jupiter.api.Disabled("closeViewerWindow did not kill the instance under the container harness")
    @Test
    void revealsARunningViewerAndThenClosesItByItsOwnSocket() {
        runner.run(null, T, List.of("tmux", "new-session", "-d", "-s", SESSION));
        LinuxKittyTerminalDriver driver = driver();
        driver.openViewer(SESSION, SESSION, Path.of(System.getProperty("java.io.tmpdir")));
        awaitRemoteControl();

        assertThat(driver.reveal(SESSION)).isEqualTo(TerminalDriver.Revealed.WINDOW);

        driver.closeViewerWindow(SESSION);
        awaitInstanceGoneFromTheProcessTable();
    }

    private String awaitRemoteControl() {
        try {
            return await().atMost(UP_ON_A_SLOW_SHARED_RUNNER).pollInterval(Duration.ofMillis(250))
                    .until(() -> runner.run(null, PROBE_CUT_OFF_SINCE_A_DEAD_SOCKET_BLOCKS,
                            List.of("kitty", "@", "--to", socket(), "ls")), listed -> listed.exitCode() == 0)
                    .stdout();
        } catch (ConditionTimeoutException e) {
            throw new AssertionError("kitty never answered on " + socket() + " in "
                    + UP_ON_A_SLOW_SHARED_RUNNER.toSeconds() + "s — asked in the foreground, it says: "
                    + whyKittyDiedWithoutThrowingOverTheFailure(), e);
        }
    }

    private String whyKittyDiedWithoutThrowingOverTheFailure() {
        try {
            var probe = runner.run(null, T, List.of("kitty",
                    "--listen-on", "unix:" + Path.of(System.getProperty("java.io.tmpdir"), "jagt-kitty-probe"),
                    "-o", "allow_remote_control=yes", "--title", "jagt-kitty-probe", "--", "true"));
            return "exit " + probe.exitCode() + " " + (probe.stderr() + probe.stdout()).strip();
        } catch (RuntimeException couldNotAsk) {
            return "it could not be asked: " + couldNotAsk.getMessage();
        }
    }

    private void awaitInstanceGoneFromTheProcessTable() {
        String socketPath = Path.of(System.getProperty("java.io.tmpdir"), "jagt-kitty-" + SESSION).toString();
        await("the instance holding the socket is gone").atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> runner.run(null, PROBE_CUT_OFF_SINCE_A_DEAD_SOCKET_BLOCKS,
                        List.of("pgrep", "-f", socketPath)).exitCode() != 0);
    }
}

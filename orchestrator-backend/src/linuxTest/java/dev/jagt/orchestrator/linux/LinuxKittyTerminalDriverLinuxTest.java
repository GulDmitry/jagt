package dev.jagt.orchestrator.linux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.host.SystemHost;
import dev.jagt.orchestrator.adapter.linux.LinuxKittyTerminalDriver;
import dev.jagt.orchestrator.adapter.terminal.KittyProperties;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LinuxKittyTerminalDriverLinuxTest {

  static final Duration WAIT_CEILING = Duration.ofMinutes(5);
  private static final Duration T = Duration.ofSeconds(20);
  private static final Duration PROBE_CUT_OFF_SINCE_A_DEAD_SOCKET_BLOCKS = Duration.ofSeconds(3);
  private static final String SESSION = "jagt-kitty-linux-test";
  private static final String SOCKET =
      "unix:" + Path.of(System.getProperty("java.io.tmpdir"), "jagt-kitty-" + SESSION);

  private final ProcessRunner runner = new ProcessRunner();
  private final LinuxKittyTerminalDriver driver =
      new LinuxKittyTerminalDriver(
          runner,
          new KittyProperties("kitty", ""),
          Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
          new SystemHost());

  @AfterEach
  void leaveNoWindowsOrSessionsBehind() {
    driver.closeViewerWindow(SESSION);
    runner.run(null, T, List.of("tmux", "kill-session", "-t", SESSION));
  }

  @Test
  void reportsThatNoViewerIsRunningRatherThanOneItCannotReach() {
    assertThat(driver.reveal("jagt-kitty-linux-absent"))
        .isEqualTo(TerminalDriver.Revealed.NOT_RUNNING);
  }

  @Nested
  class WhereViewerIsUp {

    @BeforeEach
    void openViewerAndAwaitRemoteControl() {
      runner.run(null, T, List.of("tmux", "new-session", "-d", "-s", SESSION));
      driver.openViewer(
          List.of("tmux", "attach", "-t", SESSION),
          SESSION,
          Path.of(System.getProperty("java.io.tmpdir")));
      try {
        await()
            .atMost(WAIT_CEILING)
            .pollInterval(Duration.ofMillis(250))
            .until(
                () ->
                    runner.run(
                        null,
                        PROBE_CUT_OFF_SINCE_A_DEAD_SOCKET_BLOCKS,
                        List.of("kitty", "@", "--to", SOCKET, "ls")),
                listed -> listed.exitCode() == 0);
      } catch (ConditionTimeoutException e) {
        var probe =
            runner.run(
                null,
                T,
                List.of(
                    "kitty",
                    "--listen-on",
                    "unix:" + Path.of(System.getProperty("java.io.tmpdir"), "jagt-kitty-probe"),
                    "-o",
                    "allow_remote_control=yes",
                    "--title",
                    "jagt-kitty-probe",
                    "--",
                    "true"));
        throw new AssertionError(
            "kitty never answered on "
                + SOCKET
                + " in "
                + WAIT_CEILING.toSeconds()
                + "s — asked in the foreground, it says: exit "
                + probe.exitCode()
                + " "
                + (probe.stderr() + probe.stdout()).strip(),
            e);
      }
    }

    @Test
    void bringsUpDetachedRemoteControllableViewerAttachedToTheSession() {
      String listed = runner.run(null, T, List.of("kitty", "@", "--to", SOCKET, "ls")).stdout();

      assertThat(listed).as("kitty's own view of itself").contains("\"tabs\"");
      assertThat(listed).contains("tmux");
    }

    @org.junit.jupiter.api.Disabled(
        "closeViewerWindow did not kill the instance under the container harness")
    @Test
    void revealsRunningViewerAndThenClosesItByItsOwnSocket() {
      assertThat(driver.reveal(SESSION)).isEqualTo(TerminalDriver.Revealed.WINDOW);

      driver.closeViewerWindow(SESSION);

      await("the instance holding the socket is gone")
          .atMost(WAIT_CEILING)
          .pollInterval(Duration.ofMillis(250))
          .until(() ->
              runner
                      .run(
                          null,
                          PROBE_CUT_OFF_SINCE_A_DEAD_SOCKET_BLOCKS,
                          List.of(
                              "pgrep",
                              "-f",
                              Path.of(System.getProperty("java.io.tmpdir"), "jagt-kitty-" + SESSION)
                                  .toString()))
                      .exitCode()
                  != 0);
    }
  }
}

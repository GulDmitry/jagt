package dev.jagt.orchestrator.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProcessRunnerTest {

  @Test
  void takesNoOptionalGitLockSoReadNeverBlocksAnAgentsCommit() {
    ProcessRunner runner = new ProcessRunner();

    String optionalLocks =
        runner
            .run(
                null,
                Duration.ofSeconds(5),
                List.of("sh", "-c", "printf %s \"$GIT_OPTIONAL_LOCKS\""))
            .stdout();

    assertThat(optionalLocks).isEqualTo("0");
  }

  @Test
  void readsNothingFromTheConsoleWhenTheCommandWasGivenNoInput() {
    String stdinIsNull =
        new ProcessRunner()
            .run(
                null,
                Duration.ofSeconds(5),
                List.of(
                    "perl",
                    "-e",
                    "my @in = stat(STDIN); my @null = stat('/dev/null');"
                        + " print $in[1] == $null[1] && $in[6] == $null[6] ? 'null' : 'other';"))
            .stdout();

    assertThat(stdinIsNull).isEqualTo("null");
  }

  @Test
  void feedsTheGivenInputToTheCommandAndClosesIt() {
    String echoed =
        new ProcessRunner()
            .run(null, Duration.ofSeconds(3), java.util.Map.of(), "{\"a\":1}", List.of("cat"))
            .stdout();

    assertThat(echoed).isEqualTo("{\"a\":1}");
  }

  @Test
  void passesTheGivenEnvironmentToTheCommand() {
    String seen =
        new ProcessRunner()
            .run(
                null,
                Duration.ofSeconds(5),
                java.util.Map.of("ABC_HOOK", "x"),
                List.of("sh", "-c", "printf %s \"$ABC_HOOK\""))
            .stdout();

    assertThat(seen).isEqualTo("x");
  }

  @Test
  void launchesTheAppOutsideTheProcessGroupTheTerminalSendsInterruptTo() {
    ProcessRunner runner = new ProcessRunner();
    Process launched = runner.runDetached(null, List.of("sleep", "5"));

    try {
      String launchedGroup =
          runner
              .run(
                  null,
                  Duration.ofSeconds(5),
                  List.of("ps", "-o", "pgid=", "-p", String.valueOf(launched.pid())))
              .stdout()
              .strip();
      String ownGroup =
          runner
              .run(
                  null,
                  Duration.ofSeconds(5),
                  List.of("ps", "-o", "pgid=", "-p", String.valueOf(ProcessHandle.current().pid())))
              .stdout()
              .strip();

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
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() ->
              assertThat(
                      runner
                          .run(
                              null,
                              Duration.ofSeconds(5),
                              List.of("ps", "-o", "comm=", "-p", String.valueOf(launched.pid())))
                          .stdout()
                          .strip())
                  .isEqualTo("sleep"));
    } finally {
      launched.destroyForcibly();
    }
  }

  @Test
  void killsWhatTimedOutCommandSpawnedSoNoChildOutlivesIt(@TempDir Path dir) throws Exception {
    Path childPid = dir.resolve("pid");

    assertThatThrownBy(() ->
            new ProcessRunner()
                .run(
                    null,
                    Duration.ofMillis(500),
                    List.of("sh", "-c", "sleep 30 & echo $! > " + childPid + "; wait")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("timed out");

    long child = Long.parseLong(Files.readString(childPid).strip());
    try {
      await()
          .atMost(Duration.ofSeconds(3))
          .until(() -> ProcessHandle.of(child).filter(ProcessHandle::isAlive).isEmpty());
    } finally {
      ProcessHandle.of(child).ifPresent(ProcessHandle::destroyForcibly);
    }
  }

  @Test
  void killsTheCommandWhenTheCallerWaitingOnItIsInterrupted(@TempDir Path dir) throws Exception {
    Path pid = dir.resolve("pid");
    AtomicReference<Throwable> ended = new AtomicReference<>();
    Thread caller =
        Thread.startVirtualThread(() ->
            ended.set(
                catchThrowable(() ->
                    new ProcessRunner()
                        .run(
                            null,
                            Duration.ofSeconds(30),
                            List.of("sh", "-c", "echo $$ > " + pid + "; exec sleep 30")))));
    await()
        .ignoreExceptions()
        .atMost(Duration.ofSeconds(5))
        .until(() -> Files.readString(pid).endsWith("\n"));
    long command = Long.parseLong(Files.readString(pid).strip());

    caller.interrupt();

    try {
      await()
          .atMost(Duration.ofSeconds(3))
          .until(() -> ProcessHandle.of(command).filter(ProcessHandle::isAlive).isEmpty());
    } finally {
      ProcessHandle.of(command).ifPresent(ProcessHandle::destroyForcibly);
    }
  }

  @Test
  void tellsTheCallerItWasStoppedWithJagtWhenInterruptedWhileJagtCloses() throws Exception {
    ProcessRunner runner = new ProcessRunner();
    runner.closes();
    AtomicReference<Throwable> ended = new AtomicReference<>();
    Thread caller =
        Thread.startVirtualThread(() ->
            ended.set(
                catchThrowable(
                    () -> runner.run(null, Duration.ofSeconds(30), List.of("sleep", "30")))));

    caller.interrupt();
    caller.join(5_000);

    assertThat(ended.get()).isInstanceOf(Processes.Stopped.class);
  }

  @Test
  void killsTheCommandsStillRunningWhenJagtStops(@TempDir Path dir) throws Exception {
    ProcessRunner runner = new ProcessRunner();
    Path pid = dir.resolve("pid");
    AtomicReference<Throwable> ended = new AtomicReference<>();
    Thread.startVirtualThread(() ->
        ended.set(
            catchThrowable(() ->
                runner.run(
                    null,
                    Duration.ofSeconds(30),
                    List.of("sh", "-c", "echo $$ > " + pid + "; exec sleep 30")))));
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> Files.exists(pid) && Files.readString(pid).endsWith("\n"));
    long command = Long.parseLong(Files.readString(pid).strip());

    runner.destroyRunning();

    try {
      await()
          .atMost(Duration.ofSeconds(3))
          .until(() -> ProcessHandle.of(command).filter(ProcessHandle::isAlive).isEmpty());
    } finally {
      ProcessHandle.of(command).ifPresent(ProcessHandle::destroyForcibly);
    }
  }

  @Test
  void tellsTheCallerItsCommandWasStoppedWithJagtRatherThanThatItFailed(@TempDir Path dir)
      throws Exception {
    ProcessRunner runner = new ProcessRunner();
    Path pid = dir.resolve("pid");
    AtomicReference<Throwable> answered = new AtomicReference<>();
    Thread caller =
        Thread.startVirtualThread(() ->
            answered.set(
                catchThrowable(() ->
                    runner.run(
                        null,
                        Duration.ofSeconds(30),
                        List.of("sh", "-c", "echo $$ > " + pid + "; exec sleep 30")))));
    await()
        .ignoreExceptions()
        .atMost(Duration.ofSeconds(5))
        .until(() -> Files.readString(pid).endsWith("\n"));

    runner.destroyRunning();
    caller.join(3_000);

    assertThat(answered.get()).isInstanceOf(Processes.Stopped.class);
  }

  @Test
  void tellsTheCallerCommandEndingWhileJagtClosesWasStoppedWithItRatherThanAnswered() {
    ProcessRunner runner = new ProcessRunner();
    runner.closes();

    Throwable answered =
        catchThrowable(() -> runner.run(null, Duration.ofSeconds(5), List.of("true")));

    assertThat(answered).isInstanceOf(Processes.Stopped.class);
  }

  @Test
  void leavesTheAppInterruptibleByItsOwnTooling() throws Exception {
    Process launched = new ProcessRunner().runDetached(null, List.of("sleep", "5"));

    try {
      Process kill = new ProcessBuilder("kill", "-INT", String.valueOf(launched.pid())).start();

      assertThat(kill.waitFor(5, TimeUnit.SECONDS)).isTrue();
      assertThat(kill.exitValue()).isZero();
      assertThat(launched.waitFor(2, TimeUnit.SECONDS)).isTrue();
    } finally {
      launched.destroyForcibly();
    }
  }

  @Test
  void leavesTheAppInterruptibleWhenJagtItselfWasStartedIgnoringInterrupt() throws Exception {
    List<String> ignoringParent =
        List.of("perl", "-e", "$SIG{INT} = 'IGNORE'; exec @ARGV or exit 127;", "--");
    List<String> command = new java.util.ArrayList<>(ignoringParent);
    command.addAll(ProcessRunner.detachedFrom(List.of("sleep", "30")));
    Process launched = new ProcessBuilder(command).start();

    try {
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> launched.info().command().filter(c -> c.endsWith("/sleep")).isPresent());
      Process kill = new ProcessBuilder("kill", "-INT", String.valueOf(launched.pid())).start();

      assertThat(kill.waitFor(5, TimeUnit.SECONDS)).isTrue();
      assertThat(kill.exitValue()).isZero();
      assertThat(launched.waitFor(3, TimeUnit.SECONDS)).isTrue();
    } finally {
      launched.destroyForcibly();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "143, on SIGTERM (143)",
    "130, on SIGINT (130)",
    "0, with exit 0",
    "128, with exit 128",
    "1, with exit 1"
  })
  void namesTheSignalKilledLaunchDiedOnAndLeavesEveryOtherEndCode(int exitValue, String expected) {
    assertThat(ProcessRunner.endedBy(exitValue)).isEqualTo(expected);
  }

  @Test
  void recordsHowLaunchEndedSoDeathNobodyAskedForCanBeAttributed() {
    Process launched = mock(Process.class);
    when(launched.pid()).thenReturn(4242L);
    when(launched.exitValue()).thenReturn(143);
    when(launched.onExit()).thenReturn(CompletableFuture.completedFuture(launched));
    try (FakeLogCapture log = new FakeLogCapture(ProcessRunner.class)) {
      ProcessRunner.reportLifeOf(launched, List.of("sleep", "30"), true, System.nanoTime());

      assertThat(log.events())
          .filteredOn(event -> "process ended".equals(event.getMessage()))
          .flatExtracting(ILoggingEvent::getKeyValuePairs)
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains("pid=4242", "exit=on SIGTERM (143)");
    }
  }

  @Test
  void namesTheLaunchThatCouldNotStartInsteadOfAnsweringWithProcess() {
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

  @Test
  void answersTheExitCodeAndBothStreamsOfCommandThatFailed() {
    var result =
        new ProcessRunner()
            .run(
                null, Duration.ofSeconds(5), List.of("sh", "-c", "echo out; echo err >&2; exit 3"));

    assertThat(result.exitCode()).isEqualTo(3);
    assertThat(result.stdout()).isEqualTo("out");
    assertThat(result.stderr()).isEqualTo("err");
  }

  @Test
  void runsTheCommandInTheDirectoryItWasGiven(@TempDir Path dir) throws Exception {
    var result = new ProcessRunner().run(dir, Duration.ofSeconds(5), List.of("pwd"));

    assertThat(Path.of(result.stdout()).toRealPath()).isEqualTo(dir.toRealPath());
  }

  @Test
  void handsTheCommandTheEnvironmentItWasGiven() {
    var result =
        new ProcessRunner()
            .run(
                null,
                Duration.ofSeconds(5),
                Map.of("JAGT_PROBE", "on"),
                List.of("sh", "-c", "echo $JAGT_PROBE"));

    assertThat(result.stdout()).isEqualTo("on");
  }

  @Test
  void namesTheCommandThatCouldNotStart() {
    assertThatThrownBy(() ->
            new ProcessRunner().run(null, Duration.ofSeconds(5), List.of("jagt-no-such-binary")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to start command: jagt-no-such-binary");
  }

  @Test
  void keepsTheCallersInterruptSetAfterKillingTheCommand(@TempDir Path dir) throws Exception {
    Path pid = dir.resolve("pid");
    AtomicBoolean stillInterrupted = new AtomicBoolean();
    AtomicReference<Throwable> ended = new AtomicReference<>();
    Thread caller =
        Thread.startVirtualThread(() -> {
          ended.set(
              catchThrowable(() ->
                  new ProcessRunner()
                      .run(
                          null,
                          Duration.ofSeconds(30),
                          List.of("sh", "-c", "echo $$ > " + pid + "; exec sleep 30"))));
          stillInterrupted.set(Thread.currentThread().isInterrupted());
        });
    await()
        .ignoreExceptions()
        .atMost(Duration.ofSeconds(5))
        .until(() -> Files.readString(pid).endsWith("\n"));

    long command = Long.parseLong(Files.readString(pid).strip());

    caller.interrupt();
    caller.join(3_000);
    ProcessHandle.of(command).ifPresent(ProcessHandle::destroyForcibly);

    assertThat(stillInterrupted).isTrue();
  }

  @Test
  void namesTheLaunchWhoseWrapperExitedNonZeroAtOnce() {
    assertThatThrownBy(() -> new ProcessRunner().runDetached(null, List.of("sh", "-c", "exit 3")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("(exit 3)");
  }

  @Test
  void handsBackLauncherThatHandedOffToRunningInstanceAndExitedZero() throws Exception {
    Process launched = new ProcessRunner().runDetached(null, List.of("/bin/sh", "-c", "exit 0"));

    assertThat(launched.waitFor(5, TimeUnit.SECONDS)).isTrue();
    assertThat(launched.exitValue()).isZero();
  }

  @Test
  void keepsTheCallersInterruptSetWhenItIsInterruptedWhileLaunching() {
    Thread.currentThread().interrupt();

    Throwable thrown =
        catchThrowable(() -> new ProcessRunner().runDetached(null, List.of("sleep", "1")));

    assertThat(thrown)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Interrupted while launching");
    assertThat(Thread.interrupted()).isTrue();
  }
}

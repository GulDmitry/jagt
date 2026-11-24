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
import java.io.BufferedReader;
import java.io.StringReader;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.SmartLifecycle;

public class ProcessRunnerTest {

  public static final Duration WAIT_CEILING = Duration.ofMinutes(5);

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
  void startsCommandWhoseInputAndOutputTheCallerHolds() throws Exception {
    Process started = new ProcessRunner().start(null, List.of("cat"));

    started.getOutputStream().write("hello\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    started.getOutputStream().close();
    String echoed =
        new String(
            started.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

    assertThat(echoed).isEqualTo("hello\n");
  }

  @Test
  void startsCommandInTheFolderItWasGiven(@TempDir Path folder) throws Exception {
    Process started = new ProcessRunner().start(folder, List.of("pwd", "-P"));

    String printed =
        new String(
            started.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

    assertThat(printed.trim()).isEqualTo(folder.toRealPath().toString());
  }

  @Test
  void refusesToStartCommandThatIsNotThere() {
    assertThatThrownBy(() -> new ProcessRunner().start(null, List.of("jagt-no-such-binary")))
        .isInstanceOf(IllegalStateException.class);
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
          .atMost(WAIT_CEILING)
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
                    Duration.ofSeconds(5),
                    List.of("sh", "-c", "sleep 30 & echo $! > " + childPid + "; wait")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("timed out");

    long child = Long.parseLong(Files.readString(childPid).strip());
    CompletableFuture<?> exited = // allowed: 0237
        ProcessHandle.of(child)
            .map(ProcessHandle::onExit)
            .map(future -> (CompletableFuture<?>) future) // allowed: 0237
            .orElse(CompletableFuture.completedFuture(null));
    try {
      exited.get(60, TimeUnit.SECONDS);
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
        .atMost(WAIT_CEILING)
        .until(() -> Files.readString(pid).endsWith("\n"));
    ProcessHandle command =
        ProcessHandle.of(Long.parseLong(Files.readString(pid).strip())).orElseThrow();

    caller.interrupt();

    try {
      command.onExit().get(60, TimeUnit.SECONDS);
    } finally {
      command.destroyForcibly();
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
    caller.join(Duration.ofSeconds(60));

    assertThat(ended.get()).isInstanceOf(Processes.Stopped.class);
  }

  @Test
  void endsTheReadQuietlyWhenKillingTheCommandClosedItsStream() throws Exception {
    BufferedReader closed = new BufferedReader(new StringReader("a"));
    closed.close();

    Throwable ended =
        catchThrowable(() -> new ProcessRunner.OutputDrain(closed, new StringBuilder()).run());

    assertThat(ended).isNull();
  }

  @Test
  void runsCommandsAgainWhenPausedContextStartsItAfterStopping() {
    ProcessRunner runner = new ProcessRunner();
    runner.stop();
    runner.start();

    String printed =
        runner.run(null, Duration.ofSeconds(5), List.of("sh", "-c", "printf ok")).stdout();

    assertThat(printed).isEqualTo("ok");
  }

  @Test
  void stopsWithoutWaitingTheCommandThatStartsAfterJagtHasStopped() {
    ProcessRunner runner = new ProcessRunner();
    runner.close();

    Throwable answered =
        catchThrowable(() -> runner.run(null, Duration.ofSeconds(5), List.of("sleep", "30")));

    assertThat(answered).isInstanceOf(Processes.Stopped.class);
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
        .atMost(WAIT_CEILING)
        .until(() -> Files.exists(pid) && Files.readString(pid).endsWith("\n"));
    ProcessHandle command =
        ProcessHandle.of(Long.parseLong(Files.readString(pid).strip())).orElseThrow();

    runner.close();

    try {
      command.onExit().get(60, TimeUnit.SECONDS);
    } finally {
      command.destroyForcibly();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "trap '' TERM; "})
  void leavesNeitherTheCommandNorItsDescendantAliveWhenTheOwnerCloses(
      String ignoring, @TempDir Path dir) throws Exception {
    ProcessRunner runner = new ProcessRunner();
    Path pids = dir.resolve("pids");
    AtomicReference<Throwable> ended = new AtomicReference<>();
    Thread.startVirtualThread(() ->
        ended.set(
            catchThrowable(() ->
                runner.run(
                    null,
                    Duration.ofSeconds(60),
                    List.of(
                        "sh",
                        "-c",
                        ignoring + "sleep 30 & echo $$ $! > " + pids + "; wait; wait")))));
    await()
        .ignoreExceptions()
        .atMost(WAIT_CEILING)
        .until(() -> Files.readString(pids).endsWith("\n"));
    String[] ids = Files.readString(pids).strip().split(" ", 2);
    ProcessHandle command = ProcessHandle.of(Long.parseLong(ids[0])).orElseThrow();
    ProcessHandle descendant = ProcessHandle.of(Long.parseLong(ids[1])).orElseThrow();

    runner.stop();

    try {
      await().atMost(WAIT_CEILING).until(() -> !command.isAlive() && !descendant.isAlive());
    } finally {
      command.destroyForcibly();
      descendant.destroyForcibly();
    }
  }

  @Test
  void stopsAfterTheSessionEndingsWhichStillRunCommandsThroughIt() {
    ProcessRunner runner = new ProcessRunner();

    assertThat(runner.getPhase())
        .isBetween(SmartLifecycle.DEFAULT_PHASE - 8192, SmartLifecycle.DEFAULT_PHASE - 3073);
  }

  @Test
  void isRunningUntilItIsStopped() {
    ProcessRunner runner = new ProcessRunner();
    boolean before = runner.isRunning();

    runner.stop();

    assertThat(before).isTrue();
    assertThat(runner.isRunning()).isFalse();
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
        .atMost(WAIT_CEILING)
        .until(() -> Files.readString(pid).endsWith("\n"));

    runner.close();
    caller.join(Duration.ofSeconds(60));

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
          .atMost(WAIT_CEILING)
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
        .atMost(WAIT_CEILING)
        .until(() -> Files.readString(pid).endsWith("\n"));

    ProcessHandle command =
        ProcessHandle.of(Long.parseLong(Files.readString(pid).strip())).orElseThrow();

    caller.interrupt();
    caller.join(Duration.ofSeconds(60));
    command.destroyForcibly();

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

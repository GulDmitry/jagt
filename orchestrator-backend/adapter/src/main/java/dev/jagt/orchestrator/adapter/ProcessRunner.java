package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.adapter.process.ChildProcesses;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.OneLine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProcessRunner implements Processes, SmartLifecycle, AutoCloseable {

  /** Stops after {@code ShutdownEndings}, whose window kills still run commands through here. */
  static final int PHASE = DEFAULT_PHASE - 4096;

  /**
   * POSIX tools that can put a launch in its own session; absolute because they ARE the mechanism.
   */
  private static final List<String> SETSID = List.of("/usr/bin/setsid", "/bin/setsid");

  private static final List<String> PERL = List.of("/usr/bin/perl", "/bin/perl");

  /** Long enough for a wrapper that could not exec to have exited, short enough to be invisible. */
  private static final Duration LAUNCH_CHECK = Duration.ofMillis(200);

  private final ChildProcesses children = new ChildProcesses();
  private volatile boolean closing;

  @Override
  public Result run(@Nullable Path workingDir, Duration timeout, List<String> command) {
    return run(workingDir, timeout, Map.of(), command);
  }

  @Override
  public Result run(
      @Nullable Path workingDir, Duration timeout, Map<String, String> env, List<String> command) {
    return execute(workingDir, timeout, env, null, command);
  }

  @Override
  public Result run(
      @Nullable Path workingDir,
      Duration timeout,
      Map<String, String> env,
      String stdin,
      List<String> command) {
    return execute(workingDir, timeout, env, stdin, command);
  }

  @Override
  public Process start(@Nullable Path workingDir, List<String> command) {
    try {
      ProcessBuilder builder = new ProcessBuilder(command);
      if (workingDir != null) {
        builder.directory(workingDir.toFile());
      }
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      return children.spawn(builder);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start command: " + String.join(" ", command), e);
    }
  }

  @Override
  public void start() {
    children.reopen();
  }

  /**
   * A GUI launcher's CLI blocks until its window closes, so waiting would time out and destroy that
   * window.
   */
  @Override
  public Process runDetached(@Nullable Path workingDir, List<String> command) {
    try {
      List<String> detached = detachedFrom(command);
      if (!command.get(0).contains("/") && !Executables.onPath(command.get(0))) {
        throw new IllegalStateException(
            "Failed to launch: " + String.join(" ", command) + " (not on PATH)");
      }
      ProcessBuilder builder = new ProcessBuilder(detached);
      if (workingDir != null) {
        builder.directory(workingDir.toFile());
      }
      builder.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
      builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      long startedAt = System.nanoTime();
      Process launched = children.spawnDetached(builder);
      // The wrapper always starts, so a missing binary is not an IOException but the wrapper
      // exiting 127.
      // A launcher handing off to a running instance also exits at once, and that one exits ZERO.
      if (launched.waitFor(LAUNCH_CHECK.toMillis(), TimeUnit.MILLISECONDS)
          && launched.exitValue() != 0) {
        throw new IllegalStateException(
            "Failed to launch: "
                + String.join(" ", command)
                + " (exit "
                + launched.exitValue()
                + ")");
      }
      reportLifeOf(launched, command, !detached.equals(command), startedAt);
      return launched;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to launch: " + String.join(" ", command), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while launching: " + String.join(" ", command), e);
    }
  }

  /**
   * A session of its OWN, because the terminal delivers Ctrl-C to jagt's whole process group, and
   * SIGINT back at its default: a parent started with it ignored (a daemon born in a background
   * shell) hands the ignore to every child, and a code viewer's Stop button IS a SIGINT. Only perl
   * can reset it before the {@code exec}, so {@code setsid} alone is the fallback. Both wrappers
   * {@code exec}, so the returned process is the app itself and still killable by pid. With neither
   * tool present the command is returned unchanged — no session, but a launch.
   */
  static List<String> detachedFrom(List<String> command) {
    if (command.isEmpty()) {
      throw new IllegalArgumentException("Nothing to launch: the command is empty");
    }
    List<String> wrapped = new java.util.ArrayList<>();
    String perl = firstExecutable(PERL);
    if (perl != null) {
      wrapped.addAll(
          List.of(
              perl,
              "-MPOSIX",
              "-e",
              "POSIX::setsid(); $SIG{INT} = 'DEFAULT'; exec @ARGV or exit 127;",
              "--"));
    } else {
      String setsid = firstExecutable(SETSID);
      if (setsid == null) {
        return command;
      }
      wrapped.add(setsid);
    }
    wrapped.addAll(command);
    return List.copyOf(wrapped);
  }

  /**
   * A launch outlives the call, so only how it ENDED can later attribute a death nobody asked for.
   */
  static void reportLifeOf(
      Process launched, List<String> command, boolean ownSession, long startedAt) {
    log.atInfo()
        .setMessage("process launched")
        .addKeyValue("pid", launched.pid())
        .addKeyValue("session", ownSession ? "own" : "jagt")
        .addKeyValue("cmd", OneLine.clipped(String.join(" ", command)))
        .log();
    CompletableFuture<Void> _ =
        launched
            .onExit()
            .thenAccept(ended ->
                log.atInfo()
                    .setMessage("process ended")
                    .addKeyValue("pid", ended.pid())
                    .addKeyValue("exit", endedBy(ended.exitValue()))
                    .addKeyValue("after", Duration.ofNanos(System.nanoTime() - startedAt))
                    .log());
  }

  /**
   * Only the signal numbers that agree across platforms; anything else stays the number it exited
   * with.
   */
  private static final Map<Integer, String> SIGNALS =
      Map.of(
          1, "HUP", 2, "INT", 3, "QUIT", 6, "ABRT", 9, "KILL", 11, "SEGV", 13, "PIPE", 15, "TERM");

  /**
   * A process killed by a signal exits {@code 128 + signal}, which is what tells a kill from a
   * quit.
   */
  static String endedBy(int exitValue) {
    String signal = SIGNALS.get(exitValue - 128);
    return signal != null ? "on SIG" + signal + " (" + exitValue + ")" : "with exit " + exitValue;
  }

  private static @Nullable String firstExecutable(List<String> candidates) {
    return candidates.stream()
        .filter(candidate -> Files.isExecutable(Path.of(candidate)))
        .findFirst()
        .orElse(null);
  }

  private Result execute(
      @Nullable Path workingDir,
      Duration timeout,
      Map<String, String> env,
      @Nullable String stdin,
      List<String> command) {
    Process process = null;
    try {
      ProcessBuilder builder = new ProcessBuilder(command);
      if (workingDir != null) {
        builder.directory(workingDir.toFile());
      }
      builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
      builder.environment().putAll(env);
      // /dev/null so no subprocess steals the console's stdin, and a CLI waiting on it gets instant
      // EOF.
      if (stdin == null) {
        builder.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
      }
      process = children.spawn(builder);
      if (stdin != null) {
        feed(process, stdin);
      }
      // Drain both streams before waitFor to avoid pipe-buffer deadlock on chatty commands.
      var stdoutReader = process.inputReader();
      var stderrReader = process.errorReader();
      StringBuilder stdout = new StringBuilder();
      StringBuilder stderr = new StringBuilder();
      Thread outThread = Thread.startVirtualThread(new OutputDrain(stdoutReader, stdout));
      Thread errThread = Thread.startVirtualThread(new OutputDrain(stderrReader, stderr));
      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        children.kill(process, "timeout");
        throw new IllegalStateException(
            "Command timed out after " + timeout + ": " + String.join(" ", command));
      }
      outThread.join(2_000);
      errThread.join(2_000);
      boolean killed = children.wasStopped(process);
      if (killed || closing) {
        throw new Processes.Stopped(command);
      }
      return new Result(process.exitValue(), stdout.toString().trim(), stderr.toString().trim());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start command: " + String.join(" ", command), e);
    } catch (InterruptedException e) {
      throw interrupted(process, command, e);
    } finally {
      if (process != null) {
        children.release(process);
      }
    }
  }

  private RuntimeException interrupted(
      @Nullable Process process, List<String> command, InterruptedException e) {
    if (process != null) {
      children.kill(process, "interrupted");
    }
    Thread.currentThread().interrupt();
    if (closing) {
      return new Processes.Stopped(command);
    }
    return new IllegalStateException("Interrupted while running: " + String.join(" ", command), e);
  }

  /**
   * Reads a command's stream to its end; a stream closed under it ends the read, keeping what was
   * read.
   */
  static final class OutputDrain implements Runnable {

    private final BufferedReader reader;
    private final StringBuilder into;

    OutputDrain(BufferedReader reader, StringBuilder into) {
      this.reader = reader;
      this.into = into;
    }

    @Override
    public void run() {
      try {
        reader.lines().forEach(line -> into.append(line).append('\n'));
      } catch (UncheckedIOException e) {
        // Killing the command closes its streams; its caller reports that ending.
      }
    }
  }

  private static void feed(Process process, String stdin) {
    Thread.startVirtualThread(() -> {
      try (var out = process.getOutputStream()) {
        out.write(stdin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      } catch (IOException e) {
        // a command that exits without reading its input is judged by its exit
      }
    });
  }

  /**
   * The signal stopping jagt reaches its process group first: a command ending from here on was not
   * judged.
   */
  @EventListener(ContextClosedEvent.class)
  void closes() {
    closing = true;
  }

  @Override
  public boolean isRunning() {
    return !children.isClosed();
  }

  @Override
  public int getPhase() {
    return PHASE;
  }

  @Override
  public void stop() {
    close();
  }

  /** A detached launch outlives jagt by design, so only a command something waits on is stopped. */
  @Override
  public void close() {
    children.close();
  }
}

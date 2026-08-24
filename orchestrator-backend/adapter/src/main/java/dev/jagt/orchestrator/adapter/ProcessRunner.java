package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Processes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ProcessRunner implements Processes {

    /** POSIX tools that can put a launch in its own session; absolute because they ARE the mechanism. */
    private static final List<String> SETSID = List.of("/usr/bin/setsid", "/bin/setsid");
    private static final List<String> PERL = List.of("/usr/bin/perl", "/bin/perl");
    /** Long enough for a wrapper that could not exec to have exited, short enough to be invisible. */
    private static final Duration LAUNCH_CHECK = Duration.ofMillis(200);

    @Override
    public Result run(Path workingDir, Duration timeout, List<String> command) {
        return run(workingDir, timeout, Map.of(), command);
    }

    /**
     * A GUI launcher's CLI blocks until its window closes, so waiting would time out and then destroy the very
     * window it opened. Only a failure to START is reported; the launched app's own errors are its business.
     */
    @Override
    public Process runDetached(Path workingDir, List<String> command) {
        try {
            List<String> detached = detachedFrom(command);
            long startedAt = System.nanoTime();
            ProcessBuilder builder = new ProcessBuilder(detached);
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            builder.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process launched = builder.start();
            // The wrapper always starts, so a missing binary is no longer an IOException — it is the wrapper
            // exiting 127, which would otherwise read as "the feature is not configured". A launcher that
            // hands off to a running instance also exits at once, and that one exits ZERO.
            if (launched.waitFor(LAUNCH_CHECK.toMillis(), TimeUnit.MILLISECONDS) && launched.exitValue() != 0) {
                throw new IllegalStateException("Failed to launch: " + String.join(" ", command)
                        + " (exit " + launched.exitValue() + ")");
            }
            reportLifeOf(launched, command, !detached.equals(command), startedAt);
            return launched;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to launch: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while launching: " + String.join(" ", command), e);
        }
    }

    /**
     * The command in a session of its OWN, because the terminal delivers Ctrl-C to jagt's whole process group
     * and a child of {@code ProcessBuilder} is in it. Signal DISPOSITIONS stay default on purpose: ignoring
     * SIGINT would be inherited by everything the app then spawns, and an IDE's own Stop button IS a SIGINT.
     * Both wrappers {@code exec}, so the returned process is the app itself and can still be killed by pid.
     * Neither tool available (a minimal container without perl) leaves the command as it was: no session of its
     * own, but a launch.
     */
    static List<String> detachedFrom(List<String> command) {
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Nothing to launch: the command is empty");
        }
        List<String> wrapped = new java.util.ArrayList<>();
        String setsid = firstExecutable(SETSID);
        if (setsid != null) {
            wrapped.add(setsid);
        } else {
            String perl = firstExecutable(PERL);
            if (perl == null) {
                return command;
            }
            wrapped.addAll(List.of(perl, "-MPOSIX", "-e", "POSIX::setsid(); exec @ARGV or exit 127;", "--"));
        }
        wrapped.addAll(command);
        return List.copyOf(wrapped);
    }

    /**
     * A launch outlives the call, so how it ENDED is the only thing that can later attribute a death nobody
     * asked for — and whether it got a session of its own is what says whether a terminal could have been the
     * one to end it.
     */
    private static void reportLifeOf(Process launched, List<String> command, boolean ownSession, long startedAt) {
        log.info("Launched pid {} in {} session: {}", launched.pid(), ownSession ? "its own" : "jagt's",
                String.join(" ", command));
        launched.onExit().thenAccept(ended -> log.info("Launched pid {} ended {} after {}", ended.pid(),
                endedBy(ended.exitValue()), Duration.ofNanos(System.nanoTime() - startedAt)));
    }

    /** Only the signal numbers that agree across platforms; anything else stays the number it exited with. */
    private static final Map<Integer, String> SIGNALS = Map.of(1, "HUP", 2, "INT", 3, "QUIT", 6, "ABRT",
            9, "KILL", 11, "SEGV", 13, "PIPE", 15, "TERM");

    /** A process killed by a signal exits {@code 128 + signal}, which is what tells a kill from a quit. */
    static String endedBy(int exitValue) {
        String signal = SIGNALS.get(exitValue - 128);
        return signal != null ? "on SIG" + signal + " (" + exitValue + ")" : "with exit " + exitValue;
    }

    private static String firstExecutable(List<String> candidates) {
        return candidates.stream().filter(candidate -> Files.isExecutable(Path.of(candidate))).findFirst()
                .orElse(null);
    }

    @Override
    public Result run(Path workingDir, Duration timeout, Map<String, String> env, List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            builder.environment().putAll(env);
            // No jagt subprocess reads our stdin — feed them /dev/null so none can steal the console's own,
            // and so a CLI that waits on stdin gets immediate EOF instead of a several-second pause.
            builder.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
            Process process = builder.start();
            // Drain both streams before waitFor to avoid pipe-buffer deadlock on chatty commands.
            var stdoutReader = process.inputReader();
            var stderrReader = process.errorReader();
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread outThread = Thread.startVirtualThread(() -> stdoutReader.lines().forEach(l -> stdout.append(l).append('\n')));
            Thread errThread = Thread.startVirtualThread(() -> stderrReader.lines().forEach(l -> stderr.append(l).append('\n')));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Command timed out after " + timeout + ": " + String.join(" ", command));
            }
            outThread.join(2_000);
            errThread.join(2_000);
            return new Result(process.exitValue(), stdout.toString().trim(), stderr.toString().trim());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start command: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running: " + String.join(" ", command), e);
        }
    }
}

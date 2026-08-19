package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Processes;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
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
     * Fire-and-forget: start the process and return immediately, never waiting for or killing it.
     * For GUI launchers (`idea diff`, editors) whose CLI blocks until the IDE is ready or the window
     * closes — waiting would time out and then destroy the very window it opened. Only a failure to
     * START (bad binary) is reported; the launched app's own errors are its business.
     *
     * <p>Detached from jagt's own session too, so the terminal's Ctrl-C cannot reach it — see
     * {@link #detachedFrom}.
     */
    @Override
    public Process runDetached(Path workingDir, List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(detachedFrom(command));
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
            // No jagt subprocess reads our stdin — feed them /dev/null so none can steal the
            // Master shell's stdin (the backend's stdin is the JLine REPL), and so tools like
            // `claude -p` get immediate EOF instead of a 3s stdin wait.
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

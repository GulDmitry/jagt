package dev.jagt.orchestrator.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessRunner {

    public record ProcessResult(int exitCode, String stdout, String stderr) {

        public ProcessResult expectSuccess(String action) {
            if (exitCode != 0) {
                throw new IllegalStateException(
                        action + " failed (exit " + exitCode + "): " + (stderr.isBlank() ? stdout : stderr));
            }
            return this;
        }
    }

    public ProcessResult run(Path workingDir, Duration timeout, List<String> command) {
        return run(workingDir, timeout, Map.of(), command);
    }

    /**
     * Fire-and-forget: start the process and return immediately, never waiting for or killing it.
     * For GUI launchers (`idea diff`, editors) whose CLI blocks until the IDE is ready or the window
     * closes — waiting would time out and then destroy the very window it opened. Only a failure to
     * START (bad binary) is reported; the launched app's own errors are its business.
     */
    public void runDetached(Path workingDir, List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            builder.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to launch: " + String.join(" ", command), e);
        }
    }

    public ProcessResult run(Path workingDir, Duration timeout, Map<String, String> env, List<String> command) {
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
            return new ProcessResult(process.exitValue(), stdout.toString().trim(), stderr.toString().trim());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start command: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running: " + String.join(" ", command), e);
        }
    }
}

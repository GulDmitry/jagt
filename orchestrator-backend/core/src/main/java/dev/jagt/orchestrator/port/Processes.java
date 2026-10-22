package dev.jagt.orchestrator.port;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Running a command on this machine. Declared here because the shape of "run it and wait" is the same everywhere
 * while the way a process is DETACHED — a POSIX session, a Windows process group — is not.
 */
public interface Processes {

    record Result(int exitCode, String stdout, String stderr) {

        /** Itself, or an exception naming {@code action} and what the command actually said. */
        public Result expectSuccess(String action) {
            if (exitCode != 0) {
                throw new IllegalStateException(
                        action + " failed (exit " + exitCode + "): " + (stderr.isBlank() ? stdout : stderr));
            }
            return this;
        }
    }

    /** The command was killed because jagt is stopping: no verdict on what it ran, so never a failure to relay. */
    final class Stopped extends RuntimeException {

        public Stopped(List<String> command) {
            super("Stopped with jagt: " + String.join(" ", command));
        }
    }

    Result run(@Nullable Path workingDir, Duration timeout, List<String> command);

    Result run(@Nullable Path workingDir, Duration timeout, Map<String, String> env, List<String> command);

    /**
     * Starts a command that OUTLIVES jagt and does not share its signals. The returned handle is the command
     * itself, so {@code destroy()} still reaches it.
     */
    Process runDetached(@Nullable Path workingDir, List<String> command);
}

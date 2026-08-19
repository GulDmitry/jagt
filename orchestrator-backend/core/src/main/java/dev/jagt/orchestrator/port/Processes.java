package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Running a command on this machine. Declared here because the shape of "run it and wait" is the same everywhere
 * while the way a process is DETACHED from jagt is not — a POSIX session, a Windows process group — and nothing
 * above this line should have to know which.
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

    Result run(Path workingDir, Duration timeout, List<String> command);

    Result run(Path workingDir, Duration timeout, Map<String, String> env, List<String> command);

    /**
     * Starts a command that OUTLIVES jagt and does not share its signals: stopping jagt must not stop the editor
     * or the terminal it opened. The returned handle is the command itself, so {@code destroy()} still reaches it.
     */
    Process runDetached(Path workingDir, List<String> command);
}

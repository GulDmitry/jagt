package dev.jagt.orchestrator.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * Finds the external binaries jagt shells out to, portably. It exists because the default for {@code tmux} was
 * {@code /opt/homebrew/bin/tmux} — a macOS Homebrew path that made the whole flow fail on Linux with
 * "Failed to start command", which is how the container suite found it (see {@code docker/linux-suite.Dockerfile}).
 *
 * <p>The rules, in order:
 * <ol>
 *   <li>A value containing a separator is the human's explicit choice and is returned untouched — jagt does not
 *       second-guess a configured path, even one that does not exist yet (a mount that appears later, a symlink
 *       the human is about to create); the failure then names what they asked for.</li>
 *   <li>A bare name is looked up on {@code PATH}, which is the portable answer whenever jagt runs from a shell.</li>
 *   <li>Only if PATH has it nowhere do the known install locations get probed, because a process launched from a
 *       GUI (Finder, a launch agent) inherits a PATH that has neither Homebrew prefix in it — the original
 *       reason someone hardcoded an absolute path.</li>
 *   <li>Still nothing: the bare name is returned, so the error a human reads is {@code tmux}, not a guess.</li>
 * </ol>
 */
public final class Executables {

    /** Homebrew (arm + intel), Nix/pkgsrc, then the distro locations Linux uses. Order = most specific first. */
    private static final List<String> KNOWN_BIN_DIRS = List.of(
            "/opt/homebrew/bin", "/usr/local/bin", "/run/current-system/sw/bin", "/usr/bin", "/bin");

    private Executables() {
    }

    /** Resolves against the real PATH and filesystem. */
    public static String resolve(String configured) {
        return resolve(configured, System.getenv("PATH"), Executables::isExecutableFile);
    }

    /** The rules above as a pure function, so every branch is testable without a machine that has the binary. */
    static String resolve(String configured, String pathEnv, Predicate<Path> isExecutable) {
        String name = configured == null ? "" : configured.strip();
        if (name.isEmpty()) {
            return name;
        }
        if (name.contains("/")) {
            return name;
        }
        String onPath = firstMatch(splitPath(pathEnv), name, isExecutable);
        if (onPath != null) {
            return onPath;
        }
        String known = firstMatch(KNOWN_BIN_DIRS, name, isExecutable);
        return known != null ? known : name;
    }

    private static String firstMatch(List<String> dirs, String name, Predicate<Path> isExecutable) {
        for (String dir : dirs) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, name);
            if (isExecutable.test(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static List<String> splitPath(String pathEnv) {
        return pathEnv == null || pathEnv.isBlank() ? List.of() : List.of(pathEnv.split(":"));
    }

    private static boolean isExecutableFile(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }
}

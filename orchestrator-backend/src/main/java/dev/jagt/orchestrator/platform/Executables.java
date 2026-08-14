package dev.jagt.orchestrator.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * Finds the external binaries jagt shells out to, portably. In order:
 * <ol>
 *   <li>A value with a separator is the human's explicit choice, returned untouched even if it does not exist
 *       yet — the failure then names what they asked for.</li>
 *   <li>A bare name comes off {@code PATH}.</li>
 *   <li>Then the known install directories, because a GUI-launched process inherits a PATH with neither
 *       Homebrew prefix in it.</li>
 *   <li>Failing everything, the bare name — so the error reads {@code tmux}, not a guess.</li>
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

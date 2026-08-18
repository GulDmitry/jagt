package dev.jagt.orchestrator.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Finds the external binaries jagt shells out to, portably. In order:
 * <ol>
 *   <li>A value with a separator is the human's explicit choice, returned untouched even if it does not exist
 *       yet — the failure then names what they asked for.</li>
 *   <li>A bare name comes off {@code PATH}.</li>
 *   <li>Then the known install directories, because a GUI-launched process inherits a PATH with neither
 *       Homebrew prefix in it.</li>
 *   <li>Then inside application bundles, whose launcher never lands in any bin directory — without this a
 *       desktop application cannot be configured by name at all, which is how the IDE stopped opening.</li>
 *   <li>Failing everything, the bare name — so the error reads {@code tmux}, not a guess.</li>
 * </ol>
 */
public final class Executables {

    /** Homebrew (arm + intel), Nix/pkgsrc, then the distro locations Linux uses. Order = most specific first. */
    private static final List<String> KNOWN_BIN_DIRS = List.of(
            "/opt/homebrew/bin", "/usr/local/bin", "/run/current-system/sw/bin", "/usr/bin", "/bin");

    /** Per-user launcher directories, relative to the home directory. Absent ones simply match nothing. */
    private static final List<List<String>> HOME_BIN_DIRS = List.of(
            List.of(".local", "bin"),
            List.of("Library", "Application Support", "JetBrains", "Toolbox", "scripts"),
            List.of(".local", "share", "JetBrains", "Toolbox", "scripts"));

    /** Where application bundles live, and the path from one to its launcher. */
    private static final List<String> APP_DIRS = List.of("/Applications");
    private static final String BUNDLE_SUFFIX = ".app";
    private static final List<String> BUNDLE_BIN = List.of("Contents", "MacOS");

    private Executables() {
    }

    /** Resolves against the real PATH and filesystem. */
    public static String resolve(String configured) {
        return resolve(configured, System.getenv("PATH"), Executables::isExecutableFile);
    }

    /** True when {@link #resolve} found nothing and handed the bare name back, so a caller can say so itself. */
    public static boolean unresolved(String resolved) {
        return resolved != null && !resolved.isBlank() && !resolved.contains("/");
    }

    static String resolve(String configured, String pathEnv, Predicate<Path> isExecutable) {
        return resolve(configured, pathEnv, System.getProperty("user.home"), isExecutable,
                Executables::bundlesIn);
    }

    /** The rules above as a pure function, so every branch is testable without a machine that has the binary. */
    static String resolve(String configured, String pathEnv, String userHome, Predicate<Path> isExecutable,
                          Function<Path, List<Path>> bundles) {
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
        if (known != null) {
            return known;
        }
        String perUser = firstMatch(homeBinDirs(userHome), name, isExecutable);
        if (perUser != null) {
            return perUser;
        }
        String bundled = inBundles(name, userHome, isExecutable, bundles);
        return bundled != null ? bundled : name;
    }

    /**
     * The launcher inside an application bundle. Not an {@code if macos}: on a system without bundles the
     * directories hold none, and the search costs one listing.
     */
    private static String inBundles(String name, String userHome, Predicate<Path> isExecutable,
                                    Function<Path, List<Path>> bundles) {
        List<Path> appDirs = new ArrayList<>();
        APP_DIRS.forEach(dir -> appDirs.add(Path.of(dir)));
        if (userHome != null && !userHome.isBlank()) {
            appDirs.add(Path.of(userHome, "Applications"));
        }
        for (Path appDir : appDirs) {
            for (Path bundle : bundles.apply(appDir)) {
                Path candidate = bundle;
                for (String segment : BUNDLE_BIN) {
                    candidate = candidate.resolve(segment);
                }
                candidate = candidate.resolve(name);
                if (isExecutable.test(candidate)) {
                    return candidate.toString();
                }
            }
        }
        return null;
    }

    private static List<String> homeBinDirs(String userHome) {
        if (userHome == null || userHome.isBlank()) {
            return List.of();
        }
        List<String> dirs = new ArrayList<>();
        for (List<String> segments : HOME_BIN_DIRS) {
            Path dir = Path.of(userHome);
            for (String segment : segments) {
                dir = dir.resolve(segment);
            }
            dirs.add(dir.toString());
        }
        return dirs;
    }

    private static List<Path> bundlesIn(Path appDir) {
        if (!Files.isDirectory(appDir)) {
            return List.of();
        }
        try (var entries = Files.list(appDir)) {
            return entries.filter(entry -> entry.getFileName().toString().endsWith(BUNDLE_SUFFIX)).toList();
        } catch (IOException e) {
            return List.of();
        }
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

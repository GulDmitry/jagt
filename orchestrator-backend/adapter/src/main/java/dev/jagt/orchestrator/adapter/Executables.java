package dev.jagt.orchestrator.adapter;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Two lookups a plain {@code PATH} search does not cover: a GUI-launched process inherits a PATH with neither
 * Homebrew prefix in it, and an application bundle's launcher lands in no bin directory at all. A value with a
 * separator, and a name nothing matched, come back untouched, so a failure names what the human asked for.
 */
public final class Executables {

    /** Homebrew (arm + intel), Nix/pkgsrc, then the distro locations Linux uses. Order = most specific first. */
    private static final List<String> KNOWN_BIN_DIRS = List.of(
            "/opt/homebrew/bin", "/usr/local/bin", "/run/current-system/sw/bin", "/usr/bin", "/bin");

    private static final List<List<String>> HOME_BIN_DIRS = List.of(
            List.of(".local", "bin"),
            List.of("Library", "Application Support", "JetBrains", "Toolbox", "scripts"),
            List.of(".local", "share", "JetBrains", "Toolbox", "scripts"));

    private static final List<String> APP_DIRS = List.of("/Applications");
    private static final String BUNDLE_SUFFIX = ".app";
    private static final List<String> BUNDLE_BIN = List.of("Contents", "MacOS");

    private Executables() {
    }

    public static String resolve(@Nullable String configured) {
        return resolve(configured, System.getenv("PATH"), Executables::isExecutableFile);
    }

    /** True when {@link #resolve} found nothing and handed the bare name back. */
    public static boolean unresolved(@Nullable String resolved) {
        return resolved == null || resolved.isBlank() || !resolved.contains("/");
    }

    /** Whether a bare name is on {@code PATH} — all a plain spawn looks at, none of the fallbacks below. */
    public static boolean onPath(String name) {
        return onPath(name, System.getenv("PATH"), Executables::isExecutableFile);
    }

    static boolean onPath(String name, @Nullable String pathEnv, Predicate<Path> isExecutable) {
        return firstMatch(splitPath(pathEnv), name, isExecutable) != null;
    }

    static String resolve(@Nullable String configured, @Nullable String pathEnv, Predicate<Path> isExecutable) {
        return resolve(configured, pathEnv, System.getProperty("user.home"), isExecutable);
    }

    static String resolve(@Nullable String configured, @Nullable String pathEnv, @Nullable String userHome,
                          Predicate<Path> isExecutable) {
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
        String bundled = inBundles(name, userHome, isExecutable);
        return bundled != null ? bundled : name;
    }

    /** Not an {@code if macos}: a system without bundles holds none, and the search costs one listing. */
    private static @Nullable String inBundles(String name, @Nullable String userHome, Predicate<Path> isExecutable) {
        List<Path> appDirs = new ArrayList<>();
        APP_DIRS.forEach(dir -> appDirs.add(Path.of(dir)));
        if (userHome != null && !userHome.isBlank()) {
            appDirs.add(Path.of(userHome, "Applications"));
        }
        for (Path appDir : appDirs) {
            for (Path bundle : bundlesIn(appDir)) {
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

    private static List<String> homeBinDirs(@Nullable String userHome) {
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

    private static @Nullable String firstMatch(List<String> dirs, String name, Predicate<Path> isExecutable) {
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

    private static List<String> splitPath(@Nullable String pathEnv) {
        return pathEnv == null || pathEnv.isBlank() ? List.of() : List.of(pathEnv.split(":"));
    }

    private static boolean isExecutableFile(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }
}

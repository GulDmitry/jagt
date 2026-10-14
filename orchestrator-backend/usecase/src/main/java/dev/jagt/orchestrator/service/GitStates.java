package dev.jagt.orchestrator.service;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What git obeys around worktrees that can make it run a program or read a file: the shared and the
 * per-worktree config, and the hooks. One digest per entry, never a value: a remote URL may carry a credential.
 */
@RequiredArgsConstructor
final class GitStates {

    static final int MAX_ENTRIES = 512;
    /** Keys git writes itself as branches are created and pushed, none naming a program or a file. */
    private static final Pattern TRACKING = Pattern.compile("branch\\..+\\.(remote|merge|rebase|pushremote|description)");

    private final GitCommands git;

    /** Throws where a worktree's git cannot be read. */
    Map<String, String> read(List<Path> worktrees) {
        Map<String, Map<String, String>> byFile = new LinkedHashMap<>();
        worktrees.forEach(worktree -> read(worktree, byFile));
        Map<String, String> entries = new TreeMap<>();
        boolean fold = byFile.values().stream().mapToInt(Map::size).sum() > MAX_ENTRIES;
        byFile.forEach((file, inFile) -> {
            if (fold) {
                entries.put(file, digest(inFile.toString().getBytes(StandardCharsets.UTF_8)));
            } else {
                entries.putAll(inFile);
            }
        });
        return entries;
    }

    private void read(Path worktree, Map<String, Map<String, String>> byFile) {
        List<String> dirs = git.run(worktree, List.of("git", "rev-parse", "--path-format=absolute",
                "--absolute-git-dir", "--git-common-dir")).expectSuccess("git rev-parse in " + worktree)
                .stdout().lines().toList();
        Path gitDir = Path.of(dirs.getFirst());
        Path common = Path.of(dirs.getLast());
        Map<String, List<String>> shared = config(common, common.resolve("config"), byFile);
        Map<String, List<String>> own = config(common, gitDir.resolve("config.worktree"), byFile);
        List<String> hooksPath = own.getOrDefault("core.hookspath", shared.getOrDefault("core.hookspath", List.of()));
        hooks(hooksPath.isEmpty() ? common.resolve("hooks") : hooksDir(common, hooksPath.getLast()), byFile);
    }

    private Map<String, List<String>> config(Path common, Path file, Map<String, Map<String, String>> byFile) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        String listed = git.run(common, List.of("git", "config", "--file", file.toString(), "--list", "-z"))
                .expectSuccess("git config --list of " + file).stdout();
        Map<String, List<String>> values = new TreeMap<>();
        for (String entry : listed.split("\0")) {
            int split = entry.indexOf('\n');
            String key = split < 0 ? entry : entry.substring(0, split);
            if (!key.isBlank()) {
                values.computeIfAbsent(key, k -> new ArrayList<>()).add(split < 0 ? "" : entry.substring(split + 1));
            }
        }
        Map<String, String> inFile = byFile.computeIfAbsent(file.toString(), f -> new TreeMap<>());
        values.forEach((key, all) -> {
            if (!TRACKING.matcher(key).matches()) {
                inFile.put(key + " in " + file, digest(String.join("\0", all).getBytes(StandardCharsets.UTF_8)));
            }
        });
        return values;
    }

    /** Git resolves a relative path where it runs; the human's checkout is where every task's git meets. */
    private static Path hooksDir(Path common, String hooksPath) {
        Path named = hooksPath.startsWith("~/")
                ? Path.of(System.getProperty("user.home"), hooksPath.substring(2)) : Path.of(hooksPath);
        Path checkout = common.getParent() == null ? common : common.getParent();
        return checkout.resolve(named).normalize();
    }

    private static void hooks(Path dir, Map<String, Map<String, String>> byFile) {
        Map<String, String> inDir = byFile.computeIfAbsent(dir.toString(), d -> new TreeMap<>());
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path hook : files.filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().endsWith(".sample")).toList()) {
                inDir.put(hook.toString(), digest(Files.readAllBytes(hook))
                        + (Files.isExecutable(hook) ? "+x" : ""));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException("hooks of " + dir + " unreadable", unreadable);
        }
    }

    private static String digest(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException(absent);
        }
    }
}

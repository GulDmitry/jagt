package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.service.FileStamps;
import lombok.extern.slf4j.Slf4j;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * One file per session, in a directory named after the directory the session runs in; the newest is the session
 * still going. That name is DERIVED, not read anywhere — every character outside {@code [A-Za-z0-9-]} becomes a
 * dash — and a name this does not reconstruct answers 0.
 */
@Slf4j
final class ClaudeTranscripts {

    private static final String SUFFIX = ".jsonl";
    private static final JsonMapper JSON = new JsonMapper();

    private ClaudeTranscripts() {
    }

    /** Relocating the whole directory is supported, so a human who did must not silently lose the answer. */
    static Path projectsDir() {
        String configured = System.getenv("CLAUDE_CONFIG_DIR");
        return (configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".claude")
                : Path.of(configured)).resolve("projects");
    }

    /** The log this session is appending to — the newest in its directory — or empty where it keeps none. */
    static Optional<Path> newestLog(Path projectsDir, Path sessionDirectory) {
        Path dir = projectsDir.resolve(slug(sessionDirectory));
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> logs = Files.list(dir)) {
            return logs.filter(log -> log.getFileName().toString().endsWith(SUFFIX))
                    .max(Comparator.comparingLong(FileStamps::modified));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    static long lastEntryMillis(Path projectsDir, Path sessionDirectory) {
        Path dir = projectsDir.resolve(slug(sessionDirectory));
        // A session that wrote nothing yet, and a name this did not reconstruct, are both simply no sign.
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> logs = Files.list(dir)) {
            return logs.filter(log -> log.getFileName().toString().endsWith(SUFFIX))
                    .mapToLong(FileStamps::modified)
                    .max()
                    .orElse(0);
        } catch (IOException e) {
            log.atWarn().setMessage("session record unreadable")
                    .addKeyValue("dir", dir)
                    .addKeyValue("cause", e.toString())
                    .log();
            return 0;
        }
    }

    /**
     * Headless runs share the directory; only the interactive CLI's human-origin entries are the human's, and a line
     * typed into the window while they composed arrives glued to their words, so it is cut out rather than dropped.
     */
    static Optional<List<String>> humanSaid(Path projectsDir, Path sessionDirectory, Set<String> typedByJagt) {
        Path dir = projectsDir.resolve(slug(sessionDirectory));
        try (Stream<Path> logs = Files.list(dir)) {
            List<String> said = new ArrayList<>();
            for (Path log : logs.filter(log -> log.getFileName().toString().endsWith(SUFFIX))
                    .sorted(Comparator.comparingLong(FileStamps::modified)).toList()) {
                for (String line : Files.readAllLines(log)) {
                    typed(line).map(text -> cut(text, typedByJagt)).filter(text -> !text.isEmpty())
                            .ifPresent(said::add);
                }
            }
            return Optional.of(said);
        } catch (IOException | RuntimeException unreadable) {
            log.atError().setMessage("human words unreadable").addKeyValue("dir", dir)
                    .addKeyValue("cause", unreadable.toString())
                    .log();
            return Optional.empty();
        }
    }

    private static Optional<String> typed(String line) {
        if (!line.contains("\"human\"")) {
            return Optional.empty();
        }
        JsonNode entry;
        try {
            entry = JSON.readTree(line);
        } catch (JacksonException partial) {
            return Optional.empty();
        }
        if (!"user".equals(entry.path("type").asString("")) || !"cli".equals(entry.path("entrypoint").asString(""))
                || !"human".equals(entry.path("origin").path("kind").asString(""))) {
            return Optional.empty();
        }
        JsonNode content = entry.path("message").path("content");
        if (content.isString()) {
            return Optional.of(content.asString());
        }
        StringBuilder text = new StringBuilder();
        content.forEach(block -> text.append(block.path("text").asString("")));
        return Optional.of(text.toString());
    }

    private static String cut(String text, Set<String> typedByJagt) {
        String left = text;
        for (String line : typedByJagt) {
            left = line == null || line.isEmpty() ? left : left.replace(line, "");
        }
        return left.strip();
    }

    /** The PHYSICAL path is what the name is built from, so a worktree under a symlink still finds its logs. */
    static String slug(Path sessionDirectory) {
        return physical(sessionDirectory).toString().replaceAll("[^A-Za-z0-9-]", "-");
    }

    private static Path physical(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException e) {
            return directory.toAbsolutePath().normalize();
        }
    }
}

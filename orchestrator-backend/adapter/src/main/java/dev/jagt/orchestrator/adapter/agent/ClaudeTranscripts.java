package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.service.FileStamps;
import lombok.extern.slf4j.Slf4j;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
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
    private static final String ENTRYPOINT = "\"entrypoint\":";
    private static final String ASSISTANT = "\"assistant\"";
    private static final String TURN_END = "\"system\"";
    private static final Set<String> TURN_ENDS = Set.of("stop_hook_summary", "turn_duration");
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
                    .filter(ClaudeTranscripts::interactive)
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
     * When the session that keeps {@code sessionLog} ended its last turn; EMPTY while a turn is open or where the log
     * does not exist yet. A new prompt and a compaction are no entry of the turn, so an idle compaction does not
     * reset idle.
     */
    static OptionalLong turnEndedAt(Path sessionLog) {
        if (!Files.isRegularFile(sessionLog)) {
            return OptionalLong.empty();
        }
        long[] last = {0, 0};
        try (Stream<String> lines = Files.lines(sessionLog)) {
            lines.filter(line -> line.contains(ASSISTANT) || line.contains(TURN_END))
                    .forEach(line -> stamp(line, last));
        } catch (IOException | RuntimeException unreadable) {
            return unreadableRecord(sessionLog, unreadable);
        }
        return last[1] > 0 && last[1] >= last[0] ? OptionalLong.of(last[1]) : OptionalLong.empty();
    }

    private static OptionalLong unreadableRecord(Path path, Exception unreadable) {
        log.atWarn().setMessage("turn end unreadable").addKeyValue("path", path)
                .addKeyValue("cause", unreadable.toString()).log();
        return OptionalLong.empty();
    }

    private static void stamp(String line, long[] last) {
        try {
            JsonNode entry = JSON.readTree(line);
            String type = entry.path("type").asString("");
            long at = Instant.parse(entry.path("timestamp").asString("")).toEpochMilli();
            if ("assistant".equals(type)) {
                last[0] = at;
            } else if ("system".equals(type) && TURN_ENDS.contains(entry.path("subtype").asString(""))) {
                last[1] = at;
            }
        } catch (JacksonException | DateTimeParseException notAnEntryOfTheTurn) {
            return;
        }
    }

    /** Headless runs share the directory, and their activity is no sign of the session's. */
    private static boolean interactive(Path log) {
        try (Stream<String> lines = Files.lines(log)) {
            return lines.filter(line -> line.contains(ENTRYPOINT)).findFirst()
                    .filter(line -> line.contains(ENTRYPOINT + "\"cli\"")).isPresent();
        } catch (IOException | UncheckedIOException unreadable) {
            return false;
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

    /** The newest conversation's last human prompts and model text, oldest first; the oldest is cut to fit. */
    static Optional<String> tail(Path projectsDir, Path sessionDirectory, int maxChars) {
        Optional<Path> newest = newestLog(projectsDir, sessionDirectory);
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        List<String> said = new ArrayList<>();
        try (Stream<String> lines = Files.lines(newest.get())) {
            lines.forEach(line -> spoken(line).ifPresent(said::add));
        } catch (IOException | RuntimeException unreadable) {
            log.atWarn().setMessage("conversation unreadable").addKeyValue("path", newest.get())
                    .addKeyValue("cause", unreadable.toString()).log();
            return Optional.empty();
        }
        LinkedList<String> kept = new LinkedList<>();
        int left = maxChars;
        for (int at = said.size() - 1; at >= 0 && left > 0; at--) {
            String entry = said.get(at);
            String fitted = entry.length() > left ? entry.substring(entry.length() - left) : entry;
            kept.addFirst(fitted);
            left -= fitted.length() + 1;
        }
        return kept.isEmpty() ? Optional.empty() : Optional.of(String.join("\n", kept));
    }

    private static Optional<String> spoken(String line) {
        Optional<String> human = typed(line);
        if (human.isPresent()) {
            return human.filter(text -> !text.isBlank()).map(text -> "human: " + oneLine(text));
        }
        if (!line.contains("\"assistant\"")) {
            return Optional.empty();
        }
        try {
            JsonNode entry = JSON.readTree(line);
            if (!"assistant".equals(entry.path("type").asString(""))) {
                return Optional.empty();
            }
            StringBuilder text = new StringBuilder();
            entry.path("message").path("content").forEach(block -> {
                if ("text".equals(block.path("type").asString(""))) {
                    text.append(block.path("text").asString(""));
                }
            });
            return text.toString().isBlank() ? Optional.empty() : Optional.of("assistant: " + oneLine(text.toString()));
        } catch (JacksonException partial) {
            return Optional.empty();
        }
    }

    private static String oneLine(String text) {
        return text.strip().replaceAll("\\s*\\R\\s*", " / ");
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

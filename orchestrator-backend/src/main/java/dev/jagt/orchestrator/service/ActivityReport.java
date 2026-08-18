package dev.jagt.orchestrator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * What jagt did with nobody watching, read back from the log it already writes: the auto-review poll firing,
 * what a sweep found, every instruction relayed into a worktree, every status an agent reported for itself.
 * There is no second store on purpose: a log line and a copy of it in memory are two answers to one question.
 * The file itself is this run's only ({@code ui/SessionLog} clears it at startup), so what this shows is always
 * the session the human is looking at.
 *
 * <p>Only entries that name a task are kept. A line without one is jagt talking about itself (a port, a
 * scheduler tick), and the value of this view is the work that changed NO status — which {@code state.json}
 * history, carrying the transitions, cannot show.
 */
@Service
public class ActivityReport {

    /** How much of the tail to read: enough for a working day of entries, small enough to be free. */
    private static final int TAIL_BYTES = 256 * 1024;
    private static final int MAX_ENTRIES = 40;
    private static final int MAX_MESSAGE = 110;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final String logFile;
    private final JsonMapper json;

    public ActivityReport(@Value("${logging.file.name:}") String logFile, JsonMapper json) {
        this.logFile = logFile;
        this.json = json;
    }

    public String render() {
        if (logFile == null || logFile.isBlank()) {
            return "no log file configured (logging.file.name) — jagt keeps this record nowhere else.\n";
        }
        Path path = Path.of(logFile);
        if (!Files.isReadable(path)) {
            return "log file " + path + " is not readable yet — nothing to show.\n";
        }
        List<String> lines;
        try {
            lines = tail(path);
        } catch (IOException e) {
            return "could not read " + path + ": " + e.getMessage() + "\n";
        }
        // The NEWEST line decides whether this file can be read at all: a format switched mid-file would
        // otherwise let yesterday's JSON be presented as today's work.
        if (newestLine(lines) != null && parse(newestLine(lines)) == null) {
            return "log file " + path + " is not structured JSON (logging.structured.format.file), so jagt"
                    + " cannot read its own entries back.\n";
        }
        List<String> entries = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && entries.size() < MAX_ENTRIES; i--) {
            JsonNode event = parse(lines.get(i));
            if (event == null) {
                continue;
            }
            String task = text(event, "task");
            if (task != null) {
                entries.add(entry(event, task));
            }
        }
        String rolled = rolledOver(path);
        if (entries.isEmpty()) {
            return "nothing about any task in the last " + TAIL_BYTES / 1024 + " KB of " + path + "."
                    + rolled + "\n";
        }
        return "what jagt did on its own — newest first, from " + path + ":\n\n"
                + String.join("\n", entries) + "\n" + (rolled.isEmpty() ? "" : rolled.strip() + "\n");
    }

    /** Rolled-over files are NOT read: an hour before midnight is in a `.gz` this cannot open, so it says so. */
    private static String rolledOver(Path path) {
        Path directory = path.toAbsolutePath().getParent();
        String name = path.getFileName().toString();
        if (directory == null) {
            return "";
        }
        try (Stream<Path> siblings = Files.list(directory)) {
            long rotated = siblings.filter(sibling -> sibling.getFileName().toString().startsWith(name + "."))
                    .count();
            return rotated == 0 ? "" : " Older entries rolled over into " + rotated
                    + " archived file(s) next to it, which this does not read.";
        } catch (IOException e) {
            return "";
        }
    }

    private static String newestLine(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (!lines.get(i).isBlank()) {
                return lines.get(i);
            }
        }
        return null;
    }

    private String entry(JsonNode event, String task) {
        String alias = text(event, "alias");
        String message = text(event, "message");
        return String.format("  %-14s %-10s %s", when(event), alias == null ? task : alias,
                message == null ? "" : abbreviate(message));
    }

    private static String when(JsonNode event) {
        String stamp = text(event, "@timestamp");
        try {
            return stamp == null ? "" : WHEN.format(Instant.parse(stamp));
        } catch (RuntimeException e) {
            return stamp;
        }
    }

    private static String abbreviate(String message) {
        String oneLine = message.replace('\n', ' ').strip();
        return oneLine.length() <= MAX_MESSAGE ? oneLine : oneLine.substring(0, MAX_MESSAGE - 1) + "…";
    }

    private JsonNode parse(String line) {
        if (line.isBlank() || line.charAt(0) != '{') {
            return null;
        }
        try {
            return json.readTree(line);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Null rather than "null": a field the writer left empty must not print as one. */
    private static String text(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String asText = value.asString("");
        return asText.isBlank() ? null : asText;
    }

    /**
     * The last {@link #TAIL_BYTES} of the file as whole lines. The first line read is dropped unless the window
     * happens to start at the beginning: it is almost always the tail of an entry, and half a JSON object
     * parses as nothing.
     */
    private static List<String> tail(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            long from = Math.max(0, size - TAIL_BYTES);
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) Math.min(size, TAIL_BYTES));
            // Until full or EOF: one read may be short, and a window cut at the end drops the newest entries —
            // the ones this view is named after.
            while (buffer.hasRemaining() && channel.read(buffer, from + buffer.position()) > 0) {
                // keep reading
            }
            List<String> lines = new ArrayList<>(
                    List.of(new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8).split("\n")));
            if (from > 0 && !lines.isEmpty()) {
                lines.remove(0);
            }
            return lines;
        }
    }
}

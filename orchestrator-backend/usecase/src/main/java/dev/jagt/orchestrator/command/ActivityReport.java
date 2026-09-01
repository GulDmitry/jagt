package dev.jagt.orchestrator.command;

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
import java.util.Set;
import java.util.stream.Stream;

/** What jagt did with nobody watching, read from the log it already writes. Only entries naming a task are kept. */
@Service
public class ActivityReport {

    /** ECS's own envelope, not what jagt did. */
    private static final Set<String> ENVELOPE = Set.of("@timestamp", "message", "log", "process", "service",
            "ecs", "tags", "task", "alias", "error");
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
        // A format switched mid-file must not let yesterday's JSON pass as today's, so the newest line decides.
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

    /** Rolled-over files are not read — they are gzipped — so their existence is reported instead. */
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
                abbreviate((message == null ? "" : message) + fields(event)));
    }

    private static String fields(JsonNode event) {
        StringBuilder rendered = new StringBuilder();
        event.propertyStream()
                .filter(field -> !ENVELOPE.contains(field.getKey()))
                .filter(field -> !field.getValue().isNull() && !field.getValue().asString("").isBlank())
                .forEach(field -> rendered.append(' ').append(field.getKey()).append('=')
                        .append(field.getValue().asString("")));
        return rendered.toString();
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

    /** Null rather than "null": an empty field must not print as one. */
    private static String text(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String asText = value.asString("");
        return asText.isBlank() ? null : asText;
    }

    /** The last {@link #TAIL_BYTES} as whole lines; the first is dropped unless the window starts at byte 0. */
    private static List<String> tail(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            long from = Math.max(0, size - TAIL_BYTES);
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) Math.min(size, TAIL_BYTES));
            // One read may be short, and a window cut short at the end drops the newest entries.
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

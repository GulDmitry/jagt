package dev.jagt.orchestrator.command.host;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The last lines of jagt's own log, one readable line each; read on request and kept nowhere. */
@Service
public class LogTail {

  static final int LINES = 200;
  static final int READ_BYTES = 262_144;
  static final int MAX_LINE = 500;

  private static final Set<String> FRAMING =
      Set.of("@timestamp", "log", "process", "service", "message", "tags", "ecs", "error");
  private static final DateTimeFormatter CLOCK =
      DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private final ObjectMapper mapper;
  private final Path file;

  public LogTail(ObjectMapper mapper, @Value("${logging.file.name:jagt-backend.log}") String file) {
    this.mapper = mapper;
    this.file = Path.of(file).toAbsolutePath().normalize();
  }

  /** The tail, or the sentence saying why there is none. */
  public String render() {
    List<String> raw;
    try {
      raw = lastLines();
    } catch (IOException unreadable) {
      return "the log " + file + " could not be read: " + unreadable;
    }
    if (raw.isEmpty()) {
      return "the log " + file + " holds nothing yet";
    }
    List<String> lines = new ArrayList<>();
    lines.add("the last " + raw.size() + " lines of " + file + " (UTC):");
    raw.forEach(line -> lines.add(clipped(readable(line))));
    return String.join("\n", lines);
  }

  private List<String> lastLines() throws IOException {
    if (!file.toFile().isFile()) {
      return List.of();
    }
    try (RandomAccessFile log = new RandomAccessFile(file.toFile(), "r")) {
      long from = Math.max(0, log.length() - READ_BYTES);
      byte[] bytes = new byte[(int) (log.length() - from)];
      log.seek(from);
      log.readFully(bytes);
      List<String> all = new String(bytes, StandardCharsets.UTF_8).lines().toList();
      List<String> whole = from > 0 ? all.subList(1, all.size()) : all;
      return whole.subList(Math.max(0, whole.size() - LINES), whole.size());
    }
  }

  private String readable(String line) {
    try {
      JsonNode entry = mapper.readTree(line);
      StringBuilder text = new StringBuilder();
      text.append(CLOCK.format(Instant.parse(entry.path("@timestamp").asString(""))))
          .append(' ')
          .append(entry.path("log").path("level").asString("?"))
          .append(' ')
          .append(entry.path("message").asString(""));
      entry.properties().stream()
          .filter(field -> !FRAMING.contains(field.getKey()))
          .forEach(field ->
              text.append(' ').append(field.getKey()).append('=').append(shown(field.getValue())));
      return text.toString();
    } catch (RuntimeException notAnEntry) {
      return line;
    }
  }

  private static String shown(JsonNode value) {
    return value.isValueNode() ? value.asString() : value.toString();
  }

  private static String clipped(String line) {
    return line.length() <= MAX_LINE ? line : line.substring(0, MAX_LINE) + " [cut]";
  }
}

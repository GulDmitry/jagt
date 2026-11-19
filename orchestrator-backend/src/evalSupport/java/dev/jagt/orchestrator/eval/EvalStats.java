package dev.jagt.orchestrator.eval;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.OptionalDouble;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class EvalStats {

  static final long CEILING_BYTES = 1_048_576;

  private static final JsonMapper MAPPER = new JsonMapper();

  private EvalStats() {}

  public record Entry(
      String at,
      String suite,
      String name,
      boolean passed,
      String model,
      String brief,
      Tally tally) {}

  public record Tally(int runs, double seconds, OptionalDouble costUsd, int stalled) {}

  static String briefHash(@Nullable String file) {
    if (file == null) {
      return "unknown";
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(file)));
      return HexFormat.of().formatHex(digest).substring(0, 12);
    } catch (IOException | NoSuchAlgorithmException unreadable) {
      return "unknown";
    }
  }

  public static void record(
      @Nullable Path file,
      String name,
      boolean passed,
      int runs,
      double seconds,
      OptionalDouble costUsd,
      int stalled) {
    if (file == null) {
      return;
    }
    Entry entry =
        new Entry(
            Instant.now(Clock.system(ZoneOffset.UTC)).toString(),
            System.getProperty("eval.suite", "unknown"),
            name,
            passed,
            System.getProperty("eval.model", "default"),
            briefHash(System.getProperty("eval.brief.file")),
            new Tally(runs, seconds, costUsd, stalled));
    try {
      append(file, entry);
    } catch (IOException | JacksonException unwritable) {
      LoggerFactory.getLogger(EvalStats.class)
          .atWarn()
          .setMessage("eval stats unwritten")
          .addKeyValue("file", file.toString())
          .addKeyValue("cause", unwritable.toString())
          .log();
    }
  }

  static synchronized void append(Path file, Entry entry) throws IOException {
    try (FileChannel guard =
            FileChannel.open(
                file.resolveSibling(file.getFileName() + ".lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        FileLock _ = guard.lock()) {
      appendHeld(file, entry);
    }
  }

  private static void appendHeld(Path file, Entry entry) throws IOException {
    if (Files.isRegularFile(file) && Files.size(file) >= CEILING_BYTES) {
      Files.move(
          file,
          file.resolveSibling(file.getFileName() + ".1"),
          StandardCopyOption.REPLACE_EXISTING);
    }
    ObjectNode node =
        MAPPER
            .createObjectNode()
            .put("at", entry.at())
            .put("suite", entry.suite())
            .put("case", entry.name())
            .put("passed", entry.passed())
            .put("runs", entry.tally().runs())
            .put("model", entry.model())
            .put("brief", entry.brief())
            .put("seconds", entry.tally().seconds());
    entry.tally().costUsd().ifPresent(cost -> node.put("costUsd", cost));
    String line = node.put("stalled", entry.tally().stalled()).toString() + "\n";
    Files.writeString(
        file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }
}

package dev.jagt.orchestrator.eval;

import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class EvalStats {

    static final long CEILING_BYTES = 1_048_576;

    private static final JsonMapper MAPPER = new JsonMapper();

    private EvalStats() {
    }

    public record Entry(String at, String suite, String name, boolean passed, int runs, String model) {
    }

    public static void record(String name, boolean passed, int runs) {
        String file = System.getProperty("eval.stats");
        if (file == null) {
            return;
        }
        Entry entry = new Entry(Instant.now(Clock.system(ZoneOffset.UTC)).toString(), System.getProperty("eval.suite", "unknown"), name, passed,
                runs, System.getProperty("eval.model", "default"));
        try {
            append(Path.of(file), entry);
        } catch (IOException | JacksonException unwritable) {
            LoggerFactory.getLogger(EvalStats.class).atWarn().setMessage("eval stats unwritten")
                    .addKeyValue("file", file).addKeyValue("cause", unwritable.toString()).log();
        }
    }

    static void append(Path file, Entry entry) throws IOException {
        if (Files.isRegularFile(file) && Files.size(file) >= CEILING_BYTES) {
            Files.move(file, file.resolveSibling(file.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
        }
        String line = MAPPER.createObjectNode()
                .put("at", entry.at()).put("suite", entry.suite()).put("case", entry.name())
                .put("passed", entry.passed()).put("runs", entry.runs()).put("model", entry.model())
                .toString() + "\n";
        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}

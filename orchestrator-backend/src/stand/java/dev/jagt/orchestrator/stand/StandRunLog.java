package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class StandRunLog {

    public static final Path DIR = Path.of("build", "stand", "runs");
    static final int KEPT = 20;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public record Entry(Instant at, String scenario, String mode, String source, boolean passed, List<String> violations,
                        long wallSeconds, StandSpend spend) {
    }

    private StandRunLog() {
    }

    static synchronized void keep(Path dir, Entry entry, String rootName) {
        try {
            Files.createDirectories(dir);
            String name = String.format("%013d-%s-%s.json", entry.at().toEpochMilli(), entry.scenario(), rootName);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(name).toFile(), entry);
            fold(dir);
        } catch (IOException e) {
            System.out.println("stand run record not written to " + dir + ": " + e.getMessage());
        }
    }

    private static void fold(Path dir) throws IOException {
        List<Path> runs;
        try (Stream<Path> files = Files.list(dir)) {
            runs = files.filter(file -> file.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        for (Path old : runs.subList(0, Math.max(0, runs.size() - KEPT))) {
            Files.delete(old);
        }
    }
}

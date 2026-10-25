package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** What a run's metered calls cost, per kind; {@code unknown} names why where the file gave no figures. */
public record StandSpend(String unknown, Map<String, Figures> kinds) {

    public record Figures(long calls, double costUsd, long inputTokens, long outputTokens) {
    }

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static StandSpend read(Path usage) {
        if (!Files.isRegularFile(usage)) {
            return new StandSpend("unknown: no usage file", Map.of());
        }
        try {
            Map<String, Figures> kinds = new TreeMap<>();
            for (String line : Files.readAllLines(usage)) {
                if (!line.isBlank()) {
                    JsonNode row = MAPPER.readTree(line);
                    JsonNode figures = row.required("usage");
                    kinds.merge(row.required("kind").asString(), new Figures(figures.required("calls").asLong(),
                            figures.required("costUsd").asDouble(), figures.required("inputTokens").asLong(),
                            figures.required("outputTokens").asLong()), StandSpend::plus);
                }
            }
            return new StandSpend("", kinds);
        } catch (IOException | RuntimeException e) {
            return new StandSpend("unknown: usage file unreadable: " + e, Map.of());
        }
    }

    private static Figures plus(Figures a, Figures b) {
        return new Figures(a.calls() + b.calls(), a.costUsd() + b.costUsd(), a.inputTokens() + b.inputTokens(),
                a.outputTokens() + b.outputTokens());
    }
}

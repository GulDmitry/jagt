package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** What a run cost, per kind, read from its root; {@code unknown} names why where a source gave no figures. */
public record StandSpend(String unknown, Map<String, Figures> kinds) {

    public static final String SESSIONS = "SESSIONS";

    public record Figures(long calls, double costUsd, long inputTokens, long outputTokens) {
    }

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static StandSpend read(Path root) {
        Map<String, Figures> kinds = new TreeMap<>();
        List<String> unknown = new ArrayList<>();
        try {
            kinds.putAll(headless(root.resolve("usage-by-day.jsonl")));
        } catch (NoSuchFileException e) {
            unknown.add("no usage file");
        } catch (IOException | RuntimeException e) {
            unknown.add("usage file unreadable: " + e);
        }
        try {
            Figures sessions = sessions(root.resolve("state.json"));
            if (sessions == null) {
                unknown.add("no session spend in state.json");
            } else {
                kinds.put(SESSIONS, sessions);
            }
        } catch (NoSuchFileException e) {
            unknown.add("no state.json");
        } catch (IOException | RuntimeException e) {
            unknown.add("state.json unreadable: " + e);
        }
        return new StandSpend(unknown.isEmpty() ? "" : "unknown: " + String.join("; ", unknown), kinds);
    }

    /** Every kind summed; null while a source is unknown, since a partial sum would read as the whole. */
    @JsonProperty("total")
    public Figures total() {
        return unknown.isEmpty() ? kinds.values().stream().reduce(new Figures(0, 0, 0, 0), StandSpend::plus) : null;
    }

    private static Map<String, Figures> headless(Path usage) throws IOException {
        Map<String, Figures> kinds = new TreeMap<>();
        for (String line : Files.readAllLines(usage)) {
            if (!line.isBlank()) {
                JsonNode row = MAPPER.readTree(line);
                kinds.merge(row.required("kind").asString(), figures(row.required("usage")), StandSpend::plus);
            }
        }
        return kinds;
    }

    private static Figures sessions(Path state) throws IOException {
        if (!Files.isRegularFile(state)) {
            throw new NoSuchFileException(state.toString());
        }
        Figures sessions = null;
        for (var task : MAPPER.readTree(state.toFile()).path("tasks").properties()) {
            JsonNode usage = task.getValue().path("agentSpend").path("usage");
            if (usage.isObject()) {
                sessions = sessions == null ? figures(usage) : plus(sessions, figures(usage));
            }
        }
        return sessions;
    }

    private static Figures figures(JsonNode usage) {
        return new Figures(usage.required("calls").asLong(), usage.required("costUsd").asDouble(),
                usage.required("inputTokens").asLong(), usage.required("outputTokens").asLong());
    }

    private static Figures plus(Figures a, Figures b) {
        return new Figures(a.calls() + b.calls(), a.costUsd() + b.costUsd(), a.inputTokens() + b.inputTokens(),
                a.outputTokens() + b.outputTokens());
    }
}

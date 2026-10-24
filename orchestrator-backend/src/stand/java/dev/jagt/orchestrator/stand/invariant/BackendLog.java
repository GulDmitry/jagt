package dev.jagt.orchestrator.stand.invariant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class BackendLog {

    private static final String RELAYED = "instructions relayed";

    private BackendLog() {
    }

    static Map<String, List<Instant>> relayed(Path log) {
        Map<String, List<Instant>> relayed = new HashMap<>();
        Stream.of(log.resolveSibling(log.getFileName() + ".1"), log).filter(Files::isRegularFile)
                .flatMap(StateFiles::lines).filter(line -> line.startsWith("{") && line.contains(RELAYED))
                .map(line -> JsonMapper.builder().build().readTree(line))
                .filter(event -> event.path("message").asString().equals(RELAYED))
                .forEach(event -> relayed.computeIfAbsent(event.path("task").asString(), task -> new ArrayList<>())
                        .add(at(event)));
        relayed.values().forEach(times -> times.sort(null));
        return relayed;
    }

    private static Instant at(JsonNode event) {
        return Instant.parse(event.path("@timestamp").asString());
    }
}

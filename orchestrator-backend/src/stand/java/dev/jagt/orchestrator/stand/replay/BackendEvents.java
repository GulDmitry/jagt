package dev.jagt.orchestrator.stand.replay;

import dev.jagt.orchestrator.stand.StandFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

final class BackendEvents {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String LOG = "jagt-backend.log";

    record Event(JsonNode line) {

        boolean is(String message) {
            return line.path("message").asString("").equals(message);
        }

        String field(String key) {
            return line.path(key).asString("");
        }

        long number(String key) {
            String value = field(key);
            return value.isEmpty() ? 0 : Long.parseLong(value);
        }
    }

    private BackendEvents() {
    }

    static List<Event> read(Path root) {
        return Stream.of(root.resolve(LOG + ".1"), root.resolve(LOG)).filter(Files::isRegularFile)
                .flatMap(BackendEvents::lines).filter(line -> line.startsWith("{"))
                .map(line -> new Event(JSON.readTree(line))).toList();
    }

    private static Stream<String> lines(Path file) {
        try {
            return Files.readAllLines(file).stream();
        } catch (IOException e) {
            throw new StandFailure("cannot read " + file, e);
        }
    }
}

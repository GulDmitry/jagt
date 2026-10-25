package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

final class BackendLog {

    private static final String OPENED = "turn opened";
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private BackendLog() {
    }

    static List<RunRecord.Relay> relays(Path log) {
        return Stream.of(log.resolveSibling(log.getFileName() + ".1"), log).filter(Files::isRegularFile)
                .flatMap(StateFiles::lines).filter(line -> line.startsWith("{") && line.contains(OPENED))
                .flatMap(line -> relay(MAPPER.readTree(line), line).stream())
                .sorted(Comparator.comparing(RunRecord.Relay::at))
                .toList();
    }

    private static Optional<RunRecord.Relay> relay(JsonNode event, String line) {
        if (!event.path("message").asString().equals(OPENED)) {
            return Optional.empty();
        }
        String status = event.path("status").asString("");
        return Optional.of(new RunRecord.Relay(Instant.parse(event.path("@timestamp").asString()),
                event.path("task").asString(), List.of(event.path("step").asString().split("\\+")),
                status.isEmpty() ? null : TaskStatus.valueOf(status), line));
    }
}

package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.stand.StandFailure;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

final class StateFiles {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private StateFiles() {
    }

    static Map<String, List<RunRecord.Step>> histories(Path state) {
        Map<String, List<RunRecord.Step>> histories = new LinkedHashMap<>();
        if (!Files.isRegularFile(state)) {
            return histories;
        }
        MAPPER.readTree(state.toFile()).path("tasks").properties().forEach(task -> {
            List<RunRecord.Step> steps = new ArrayList<>();
            task.getValue().path("history").forEach(step -> steps.add(new RunRecord.Step(
                    TaskStatus.valueOf(step.path("status").asString()), Instant.ofEpochMilli(step.path("at").asLong()),
                    step.path("origin").asString(""))));
            histories.put(task.getKey(), List.copyOf(steps));
        });
        return histories;
    }

    static List<RunRecord.Miss> misses(Path file) {
        return Stream.of(file.resolveSibling(file.getFileName() + ".1"), file).filter(Files::isRegularFile)
                .flatMap(StateFiles::lines).filter(line -> !line.isBlank()).map(StateFiles::miss).toList();
    }

    private static RunRecord.Miss miss(String line) {
        JsonNode miss = MAPPER.readTree(line);
        return new RunRecord.Miss(Instant.ofEpochMilli(miss.path("at").asLong()), miss.path("task").asString(),
                miss.path("step").asString(), miss.path("status").asString(), line);
    }

    static Stream<String> lines(Path file) {
        try {
            return Files.readAllLines(file).stream();
        } catch (IOException e) {
            throw new StandFailure("cannot read " + file, e);
        }
    }
}

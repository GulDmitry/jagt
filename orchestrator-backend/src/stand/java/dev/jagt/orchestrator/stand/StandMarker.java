package dev.jagt.orchestrator.stand;

import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public record StandMarker(String scenario, long pid, String outcome, long jagtPid, int port) {

    public static final String FILE = "stand.json";
    public static final String RUNNING = "running";
    public static final String PASSED = "passed";
    public static final String FAILED = "failed";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static StandMarker started(String scenario) {
        return new StandMarker(scenario, ProcessHandle.current().pid(), RUNNING, 0, 0);
    }

    public static StandMarker read(Path root, long pidFromName) {
        Path file = root.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return new StandMarker("unknown", pidFromName, RUNNING, 0, 0);
        }
        return MAPPER.readValue(file.toFile(), StandMarker.class);
    }

    public StandMarker withJagt(long jagtPid, int port) {
        return new StandMarker(scenario, pid, outcome, jagtPid, port);
    }

    public StandMarker withOutcome(String outcome) {
        return new StandMarker(scenario, pid, outcome, jagtPid, port);
    }

    public boolean failed() {
        return FAILED.equals(outcome);
    }

    public StandMarker writeTo(Path root) {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(root.resolve(FILE).toFile(), this);
        return this;
    }
}

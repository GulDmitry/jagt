package dev.jagt.orchestrator.stand;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class Pipelines {

    private final StandStore store;
    private final Duration delay;
    private final Map<Integer, Instant> running = new HashMap<>();

    public Pipelines(StandStore store, Duration delay) {
        this.store = store;
        this.delay = delay;
    }

    public void passDue(Timeline timeline) {
        Instant now = Instant.now();
        for (JsonNode request : store.allRequests()) {
            if (!request.path("pipeline").asString().equals("running")) {
                continue;
            }
            int id = request.path("id").asInt();
            if (now.isAfter(running.computeIfAbsent(id, seen -> now).plus(delay))) {
                store.setPipeline(id, "success");
                running.remove(id);
                timeline.note("pipeline of merge request " + id + " set success");
            }
        }
    }
}

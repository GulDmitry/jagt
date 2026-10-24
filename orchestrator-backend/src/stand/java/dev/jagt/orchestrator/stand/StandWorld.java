package dev.jagt.orchestrator.stand;

import java.nio.file.Path;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

public interface StandWorld {

    StandStore store();

    StandProject project();

    Path scratch(String name);

    void note(String event);

    int relaysSeen();

    void say(String line);

    void restart();

    default Optional<JsonNode> openRequest() {
        return store().allRequests().stream().filter(request -> request.path("state").asString().equals("open"))
                .findFirst();
    }
}

package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class StandStoreTest {

    @TempDir
    Path directory;

    @Test
    void listsMergeRequestsInTheOrderTheyWereOpenedPastNine() {
        StandStore store = new StandStore(directory, "http://127.0.0.1:1");
        Stream.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j").forEach(branch -> store.createRequest(branch, "main", branch, ""));

        List<JsonNode> requests = store.allRequests();

        assertThat(requests).extracting(request -> request.path("id").asInt()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }
}

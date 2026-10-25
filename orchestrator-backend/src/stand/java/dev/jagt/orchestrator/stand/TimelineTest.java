package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TimelineTest {

    private static final String STOPPED = """
            {"tasks":{"ABC-42":{"status":"IN_PROGRESS",
              "stop":{"trigger":"MISS","at":1,"evidence":"push to main refused"}}}}""";
    private static final String BOARD = """
            {"tasks":[{"id":"ABC-42","owner":"YOU"}]}""";

    @Test
    void aMasterReadIsNotedOnceAndBreaksTheQuiet(@TempDir Path dir) throws Exception {
        Timeline timeline = new Timeline(dir.resolve("timeline.log"), mock(ContextRelays.class));

        timeline.masterReads(1);
        timeline.masterReads(1);

        assertThat(Files.readAllLines(dir.resolve("timeline.log"))).singleElement().asString().endsWith("stand master read");
    }

    @Test
    void aStoppedTaskIsReadWithItsTriggerAndEvidenceAndNotedOnce(@TempDir Path dir) throws Exception {
        Timeline timeline = new Timeline(dir.resolve("timeline.log"), mock(ContextRelays.class));
        JsonNode state = JsonMapper.builder().build().readTree(STOPPED);
        JsonNode board = JsonMapper.builder().build().readTree(BOARD);

        timeline.record(state, board);
        timeline.record(state, board);

        assertThat(timeline.stop()).contains(new Timeline.Stop("MISS", "push to main refused"));
        assertThat(Files.readAllLines(dir.resolve("timeline.log")).stream().filter(line -> line.contains("stopped")))
                .singleElement().asString().contains("stopped, human needed: MISS: push to main refused");
    }
}

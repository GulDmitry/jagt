package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.PressLog.Interaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PressLogTest {

    private static final long NOW = 1_800_000_000_000L;

    @TempDir
    Path dir;

    @Test
    void writesOneLinePerPress() throws IOException {
        Path file = dir.resolve("interactions.jsonl");
        PressLog presses = new PressLog(new JsonMapper(), file, true, 1024);

        presses.record(List.of(new Interaction(NOW, "action:ship", "REVIEW_PENDING"),
                new Interaction(NOW, "id:open-palette", null)), NOW);

        assertThat(Files.readAllLines(file)).containsExactly(
                "{\"at\":1800000000000,\"control\":\"action:ship\",\"status\":\"REVIEW_PENDING\"}",
                "{\"at\":1800000000000,\"control\":\"id:open-palette\",\"status\":null}");
    }

    @Test
    void rollsIntoOneArchiveReplacingTheLastWhenPastItsCeiling() throws IOException {
        Path file = dir.resolve("interactions.jsonl");
        Files.writeString(dir.resolve("interactions.jsonl.1"), "older archive\n");
        PressLog presses = new PressLog(new JsonMapper(), file, true, 40);

        presses.record(List.of(new Interaction(NOW, "action:deploy", "APPROVED")), NOW);

        assertThat(file).doesNotExist();
        assertThat(Files.readString(dir.resolve("interactions.jsonl.1"))).contains("action:deploy").doesNotContain("older");
    }

    @ParameterizedTest
    @CsvSource({
            "'Ship it', REVIEW_PENDING, 1800000000000",
            "action:ship, 'ABC-42', 1800000000000",
            "action:ship, REVIEW_PENDING, 1700000000000"})
    void refusesTheWholeBatchWhenOnePressCarriesMoreThanAControlAndAStatus(String control, String status, long at) {
        Path file = dir.resolve("interactions.jsonl");
        PressLog presses = new PressLog(new JsonMapper(), file, true, 1024);

        assertThatThrownBy(() -> presses.record(List.of(new Interaction(NOW, "action:done", null),
                new Interaction(at, control, status)), NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(file).doesNotExist();
    }

    @Test
    void refusesABatchHoldingMoreThanFiftyPresses() {
        PressLog presses = new PressLog(new JsonMapper(), dir.resolve("interactions.jsonl"), true, 1024);

        assertThatThrownBy(() -> presses.record(Collections.nCopies(51, new Interaction(NOW, "flag", null)), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writesNothingWhenSwitchedOff() {
        Path file = dir.resolve("interactions.jsonl");
        PressLog presses = new PressLog(new JsonMapper(), file, false, 1024);

        presses.record(List.of(new Interaction(NOW, "action:ship", null)), NOW);

        assertThat(file).doesNotExist();
    }
}

package dev.jagt.orchestrator.adapter.replay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class RecordedReadsTest {

    @Test
    void answersEachReadKindInRecordedOrderThenRepeatsTheLastForEveryLaterPoll(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"REVIEW_SWEEP","ref":"mr-1","answer":{"approved":false}}
                {"type":"read","kind":"TICKET_READ","ref":"ABC-1","answer":{"title":"first"}}
                {"type":"read","kind":"REVIEW_SWEEP","ref":"mr-1","answer":{"approved":true}}
                """);
        RecordedReads reads = new RecordedReads(new Recording(file.toString()));

        Optional<JsonNode> firstPoll = reads.answer("REVIEW_SWEEP", "mr-1");
        Optional<JsonNode> secondPoll = reads.answer("REVIEW_SWEEP", "mr-1");
        Optional<JsonNode> laterPoll = reads.answer("REVIEW_SWEEP", "mr-1");
        Optional<JsonNode> ticket = reads.answer("TICKET_READ", "ABC-1");

        assertThat(firstPoll.orElseThrow().path("approved").asBoolean()).isFalse();
        assertThat(secondPoll.orElseThrow().path("approved").asBoolean()).isTrue();
        assertThat(laterPoll.orElseThrow().path("approved").asBoolean()).isTrue();
        assertThat(ticket.orElseThrow().path("title").asString()).isEqualTo("first");
    }

    @Test
    void endsTheReplayAndAnswersNothingWhereTheRunNeverMadeThatRead(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"TICKET_READ","ref":"ABC-1","answer":{"title":"first"}}
                """);
        Recording recording = new Recording(file.toString());
        RecordedReads reads = new RecordedReads(recording);

        Optional<JsonNode> answer = reads.answer("INTAKE", "assignee = me");

        assertThat(answer).isEmpty();
        assertThat(recording.diverged()).isTrue();
    }

    @Test
    void keepsTheReplayGoingWhereTheReadWasRecorded(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"TICKET_READ","ref":"ABC-1","answer":{"title":"first"}}
                """);
        Recording recording = new Recording(file.toString());

        new RecordedReads(recording).answer("TICKET_READ", "ABC-1");

        assertThat(recording.diverged()).isFalse();
    }
}

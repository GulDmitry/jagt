package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.replay.RecordedReads;
import dev.jagt.orchestrator.adapter.replay.Recording;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.TicketFacts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayTrackerAssistantTest {

    @Test
    void aReplayReadsTheTicketAsTheRunsModelAnsweredIt(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"TICKET_READ","ref":"ABC-1","answer":{"exists":true,"key":"ABC-1","title":"Fix the widget"}}
                """);
        ReplayTrackerAssistant assistant = new ReplayTrackerAssistant(new RecordedReads(new Recording(file.toString())));

        Answer<TicketFacts> answer = assistant.readTicket("ABC-1", List.of());

        assertThat(answer.facts().orElseThrow().title()).isEqualTo("Fix the widget");
    }

    @Test
    void aReplayHasNoTicketWhereTheRunNeverReadOneAndDiverges(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, "\n");
        Recording recording = new Recording(file.toString());
        ReplayTrackerAssistant assistant = new ReplayTrackerAssistant(new RecordedReads(recording));

        Answer<TicketFacts> answer = assistant.readTicket("ABC-1", List.of());

        assertThat(answer.facts()).isEmpty();
        assertThat(recording.diverged()).isTrue();
    }

    @Test
    void aReplayHasNoTicketWhereTheRecordedReadFailedAndKeepsItsCause(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"TICKET_READ","ref":"ABC-1","answer":{"failure":"tracker unreachable"}}
                """);
        ReplayTrackerAssistant assistant = new ReplayTrackerAssistant(new RecordedReads(new Recording(file.toString())));

        Answer<TicketFacts> answer = assistant.readTicket("ABC-1", List.of());

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.failure()).isEqualTo("tracker unreachable");
    }

    @Test
    void aReplayReadsTheTicketTextTheMasterReviewedStripped(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"MASTER_REVIEW","ref":"ABC-1","answer":{"text":"  Greet World.  "}}
                """);
        ReplayTrackerAssistant assistant = new ReplayTrackerAssistant(new RecordedReads(new Recording(file.toString())));

        Answer<String> answer = assistant.readTicketText("ABC-1");

        assertThat(answer.facts()).contains("Greet World.");
    }

    @Test
    void aReplayHasNoTicketTextWhereTheRecordedTextWasBlank(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"MASTER_REVIEW","ref":"ABC-1","answer":{"text":"   "}}
                """);
        ReplayTrackerAssistant assistant = new ReplayTrackerAssistant(new RecordedReads(new Recording(file.toString())));

        Answer<String> answer = assistant.readTicketText("ABC-1");

        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void aReplayFindsTheCandidatesIntakeRecordedForAnyQuery(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"INTAKE","ref":"assignee = me","answer":{"keys":["ABC-1"," ABC-2 ",""]}}
                """);
        ReplayTrackerAssistant assistant = new ReplayTrackerAssistant(new RecordedReads(new Recording(file.toString())));

        Answer<List<String>> answer = assistant.findCandidates("another query");

        assertThat(answer.facts().orElseThrow()).containsExactly("ABC-1", "ABC-2");
    }
}

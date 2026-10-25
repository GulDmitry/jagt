package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.replay.RecordedReads;
import dev.jagt.orchestrator.adapter.replay.Recording;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayCodeHostAssistantTest {

    @Test
    void aReplayReadsTheMergeRequestAsTheRunsModelAnsweredIt(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"MR_READ","ref":"mr-1","answer":{"exists":true,"sourceBranch":"abc-1","targetBranch":"main","title":"Fix"}}
                """);
        ReplayCodeHostAssistant assistant = new ReplayCodeHostAssistant(new RecordedReads(new Recording(file.toString())));

        Answer<MergeRequestFacts> answer = assistant.readMergeRequest("mr-1");

        assertThat(answer.facts().orElseThrow().sourceBranch()).isEqualTo("abc-1");
    }

    @Test
    void aReplayReadsTheReviewAsTheRunsModelAnsweredIt(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"REVIEW_SWEEP","ref":"mr-1","answer":{"exists":true,"approved":true,"pipelineStatus":"success"}}
                """);
        ReplayCodeHostAssistant assistant = new ReplayCodeHostAssistant(new RecordedReads(new Recording(file.toString())));

        Answer<ReviewFacts> answer = assistant.readReview("mr-1");

        assertThat(answer.facts().orElseThrow().approved()).isTrue();
    }

    @Test
    void aReplayHasNoReviewWhereTheRunNeverSweptAndDiverges(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, "\n");
        Recording recording = new Recording(file.toString());
        ReplayCodeHostAssistant assistant = new ReplayCodeHostAssistant(new RecordedReads(recording));

        Answer<ReviewFacts> answer = assistant.readReview("mr-1");

        assertThat(answer.facts()).isEmpty();
        assertThat(recording.diverged()).isTrue();
    }

    @Test
    void aReplayHasNoReviewWhereTheRecordedReadFailedAndKeepsItsCause(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"read","kind":"REVIEW_SWEEP","ref":"mr-1","answer":{"failure":"host unreachable"}}
                """);
        ReplayCodeHostAssistant assistant = new ReplayCodeHostAssistant(new RecordedReads(new Recording(file.toString())));

        Answer<ReviewFacts> answer = assistant.readReview("mr-1");

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.failure()).isEqualTo("host unreachable");
    }
}

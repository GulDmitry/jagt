package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.replay.RecordedVerdicts;
import dev.jagt.orchestrator.adapter.replay.Recording;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.RoundReviewer.Round;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayRoundReviewerTest {

    @Test
    void aReplayedReadAnswersWithTheVerdictTheRecordingHeldAtNoCost(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"verdict","round":1,"role":"QA","judgement":{"failure":"","verdict":"ready","findings":[],"question":"","premises":[],"newTask":""}}
                """);
        ReplayRoundReviewer reviewer = new ReplayRoundReviewer(new RecordedVerdicts(new Recording(file.toString())));

        Answer<Judgement> answer = reviewer.review(new Round("", "<role>You are the QA</role>", List.of(), ""));

        assertThat(answer.facts().orElseThrow().verdict()).isEqualTo("ready");
        assertThat(answer.usage().isNone()).isTrue();
    }

    @Test
    void aReadPastTheRecordingIsAFailedJudgementNamingWhy(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, "\n");
        ReplayRoundReviewer reviewer = new ReplayRoundReviewer(new RecordedVerdicts(new Recording(file.toString())));

        Answer<Judgement> answer = reviewer.review(new Round("", "<role>You are the QA</role>", List.of(), ""));

        assertThat(answer.facts().orElseThrow().failure()).isEqualTo("the recording holds no verdict for this read");
    }

    @Test
    void aReplayedReadLoadsTheMcpServerSoATicketTheRoundDoesNotQuoteStaysReadable(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, "\n");
        ReplayRoundReviewer reviewer = new ReplayRoundReviewer(new RecordedVerdicts(new Recording(file.toString())));

        assertThat(reviewer.loadsMcpServer()).isTrue();
    }
}

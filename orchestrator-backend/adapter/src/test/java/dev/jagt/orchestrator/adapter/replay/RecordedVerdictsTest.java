package dev.jagt.orchestrator.adapter.replay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordedVerdictsTest {

    @Test
    void answersARoleReadWithTheVerdictThatRoleGaveInTheRound(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"verdict","round":1,"role":"architect","judgement":{"failure":"","verdict":"not ready","findings":[],"question":"","premises":[],"newTask":""}}
                {"type":"verdict","round":1,"role":"QA","judgement":{"failure":"","verdict":"ready","findings":[],"question":"","premises":[],"newTask":""}}
                """);
        RecordedVerdicts verdicts = new RecordedVerdicts(new Recording(file.toString()));

        Judgement read = verdicts.answer("<role>You are the QA of jagt's unattended reviewer</role>").orElseThrow();

        assertThat(read.verdict()).isEqualTo("ready");
    }

    @Test
    void answersAQuestionWithTheRecordedDecisionAsItsFinding(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"answer","question":"Keep the empty argument?","decision":"Greet World too."}
                """);
        RecordedVerdicts verdicts = new RecordedVerdicts(new Recording(file.toString()));

        Judgement read = verdicts.answer("<role>You stand in for the human on task ABC-1.</role>\n" + RoundReviewer.QUESTION_LEAD + "\n<question>\nKeep it?\n</question>").orElseThrow();

        assertThat(read.findings()).extracting(RoundReviewer.Finding::issue).containsExactly("Greet World too.");
    }

    @Test
    void endsTheReplayWhenTheMasterAnswersPastTheRecording(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, "\n");
        Recording recording = new Recording(file.toString());

        new RecordedVerdicts(recording).answer(RoundReviewer.QUESTION_LEAD + "\n<question>\nKeep it?\n</question>");

        assertThat(recording.diverged()).isTrue();
    }

    @Test
    void endsTheReplayWhenTheMasterReadsPastTheRecording(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, "\n");
        Recording recording = new Recording(file.toString());

        new RecordedVerdicts(recording).answer("<role>You judge the plan of task ABC-1</role>");

        assertThat(recording.diverged()).isTrue();
    }

    @Test
    void answersFromTheEarliestRoundLeftEvenWhereALaterRoundHoldsTheRoleNamed(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, """
                {"type":"verdict","round":1,"role":"architect","judgement":{"failure":"","verdict":"not ready","findings":[],"question":"","premises":[],"newTask":""}}
                {"type":"verdict","round":2,"role":"QA","judgement":{"failure":"","verdict":"ready","findings":[],"question":"","premises":[],"newTask":""}}
                """);
        RecordedVerdicts verdicts = new RecordedVerdicts(new Recording(file.toString()));

        Judgement read = verdicts.answer("<role>You are the QA of jagt's unattended reviewer</role>").orElseThrow();

        assertThat(read.verdict()).isEqualTo("not ready");
    }
}

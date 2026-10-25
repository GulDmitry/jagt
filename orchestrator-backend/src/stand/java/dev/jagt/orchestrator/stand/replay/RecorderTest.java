package dev.jagt.orchestrator.stand.replay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.replay.RecordingLine;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecorderTest {

    @Test
    void recordsEachMoveUnderTheTurnItAnsweredAndARestampedVerdictOnce() throws Exception {
        Path root = Path.of(RecorderTest.class.getResource("/runs/recorded").toURI());

        List<RecordingLine> recorded = Recorder.record(root, Duration.ofSeconds(5));

        assertThat(recorded).extracting(line -> switch (line) {
            case RecordingLine.Read read -> "read " + read.kind();
            case RecordingLine.Called called -> "call " + called.turn() + " " + called.arguments().path("status").asString();
            case RecordingLine.Wrote wrote -> "tree " + wrote.turn() + " " + wrote.files().keySet();
            case RecordingLine.Opened opened -> "turn " + opened.turn() + " " + opened.step();
            case RecordingLine.Judged judged -> "verdict " + judged.round() + " " + judged.role();
            case RecordingLine.Pushed pushed -> "push " + pushed.turn();
            case RecordingLine.Answered answered -> "answer " + answered.question();
        }).containsExactly("read TICKET_READ", "call 0 IN_PROGRESS", "tree 0 [task_notes.md]", "call 0 REVIEW_PENDING",
                "turn 1 ship", "turn 2 deploy", "verdict 7 QA");
    }

    @Test
    void recordsEachDecisionTheMasterGaveAQuestionOnceAndNoTreeOfItsOwn() throws Exception {
        Path root = Path.of(RecorderTest.class.getResource("/runs/answered").toURI());

        List<RecordingLine> recorded = Recorder.record(root, Duration.ofSeconds(5));

        assertThat(recorded).containsExactly(
                new RecordingLine.Answered("Keep the empty argument?", "Greet World too, since an empty name reads as none."),
                new RecordingLine.Answered("Touch the README?", "No, the ticket does not ask."));
    }
}

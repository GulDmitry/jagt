package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestCheckerRunBuilder.checkerRun;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.replay.RecordedReads;
import dev.jagt.orchestrator.adapter.replay.Recording;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayCheckerReaderTest {

  @TempDir Path root;

  @Test
  void readsTheVerdictAndTheFlowsTheRecordedAcceptanceCheckAnswered() throws Exception {
    Path file = root.resolve("recording.jsonl");
    Files.writeString(
        file,
        """
        {"type":"read","kind":"ACCEPTANCE_CHECK","ref":"abc123","answer":{"failure":"","rows":[{"criterion":"The page greets","status":"unprovable","evidence":"flow needed"}],"question":"","waitingOn":"","flows":["@AC-1"],"environment":"http://127.0.0.1:1/app"}}
        """);
    CheckerReader reader =
        new ReplayCheckerReader(new RecordedReads(new Recording(file.toString())));

    AcceptanceVerdict verdict =
        reader.read(checkerRun().withWorktree(root).build()).facts().orElseThrow();

    assertThat(verdict.flows().ids()).containsExactly("@AC-1");
    assertThat(verdict.flows().environment()).isEqualTo("http://127.0.0.1:1/app");
    assertThat(verdict.rows())
        .extracting(AcceptanceVerdict.Row::criterion)
        .containsExactly("The page greets");
    assertThat(root.resolve("acceptance-report.md")).exists();
  }

  @Test
  void failsOnlyWhereTheRecordingHoldsNoAcceptanceCheck() throws Exception {
    Path file = root.resolve("recording.jsonl");
    Files.writeString(file, "\n");
    CheckerReader reader =
        new ReplayCheckerReader(new RecordedReads(new Recording(file.toString())));

    var answer = reader.read(checkerRun().withWorktree(root).build());

    assertThat(answer.facts().orElseThrow().failure())
        .contains("the recording holds no acceptance check");
  }

  @Test
  void failsTheReconsiderationBecauseNoRecordingHoldsOne() throws Exception {
    Path file = root.resolve("recording.jsonl");
    Files.writeString(file, "\n");
    CheckerReader reader =
        new ReplayCheckerReader(new RecordedReads(new Recording(file.toString())));

    var answer = reader.reconsider(checkerRun().withWorktree(root).build());

    assertThat(answer.facts().orElseThrow().toString())
        .contains("the recording holds no acceptance check");
  }
}

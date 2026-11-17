package dev.jagt.orchestrator.stand.replay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.replay.RecordingLine;
import dev.jagt.orchestrator.stand.StandProject;
import dev.jagt.orchestrator.stand.StandShell;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecorderPushedTest {

  @TempDir Path root;

  @Test
  void theCodeSessionPushedIsRecordedBeforeItsHandBackAndItsRequestReport() throws Exception {
    StandProject project = StandProject.create(root);
    Path work = project.repo();
    StandShell.git(work, "checkout", "-b", "ABC-1");
    Files.writeString(work.resolve("greet.sh"), "#!/bin/sh\necho hi\n");
    StandShell.git(work, "commit", "-am", "Greet differently");
    StandShell.git(work, "push", "origin", "ABC-1");
    Files.writeString(
        root.resolve("jagt-backend.log"),
        """
        {"@timestamp":"2026-10-24T00:00:10Z","message":"mcp tool called","task":"ABC-1","turn":"0","tool":"update_agent_status","arguments":"{\\"status\\":\\"REVIEW_PENDING\\"}"}
        {"@timestamp":"2026-10-24T00:00:20Z","message":"mcp tool called","task":"ABC-1","turn":"0","tool":"update_agent_status","arguments":"{\\"reviewRequestUrl\\":\\"http://x/requests/1\\"}"}
        """);

    List<RecordingLine> recorded = Recorder.record(root, Duration.ofSeconds(5));

    assertThat(recorded)
        .extracting(line -> line.getClass().getSimpleName())
        .containsExactly("Wrote", "Called", "Pushed", "Called");
    assertThat(recorded.get(0))
        .isInstanceOfSatisfying(
            RecordingLine.Wrote.class, wrote -> assertThat(wrote.files()).containsKey("greet.sh"));
    assertThat(recorded.get(2))
        .isInstanceOfSatisfying(
            RecordingLine.Pushed.class,
            pushed -> assertThat(pushed.message()).isEqualTo("Greet differently"));
  }

  @Test
  void taskWhoseBranchWasNeverPushedRecordsNoCode() throws Exception {
    StandProject.create(root);
    Files.writeString(
        root.resolve("jagt-backend.log"),
        """
        {"@timestamp":"2026-10-24T00:00:10Z","message":"mcp tool called","task":"ABC-1","turn":"0","tool":"update_agent_status","arguments":"{\\"status\\":\\"REVIEW_PENDING\\"}"}
        """);

    List<RecordingLine> recorded = Recorder.record(root, Duration.ofSeconds(5));

    assertThat(recorded).hasSize(1);
  }

  @Test
  void writingRecordingPlacesItsFileUnderTheRoot() {
    Path written = Recorder.write(root, Duration.ofSeconds(5));

    assertThat(written).isEqualTo(root.resolve("recording.jsonl")).exists();
  }
}

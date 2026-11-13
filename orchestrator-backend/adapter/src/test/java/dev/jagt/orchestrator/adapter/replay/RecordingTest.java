package dev.jagt.orchestrator.adapter.replay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordingTest {

  @Test
  void readsBackTheLinesWriteRecorded(@TempDir Path root) {
    Path file = root.resolve("recording.jsonl");
    List<RecordingLine> lines =
        List.of(
            new RecordingLine.Opened("ABC-1", 1, "ship"),
            new RecordingLine.Wrote("ABC-1", 1, Map.of("a.txt", "done")));

    Recording.write(file, lines);

    assertThat(Recording.read(file)).isEqualTo(lines);
  }

  @Test
  void startsReplayingAsNotDivergedAndIsDivergedOnceAnythingDiverges(@TempDir Path root)
      throws Exception {
    Path file = root.resolve("recording.jsonl");
    Files.writeString(file, "\n");
    Recording recording = new Recording(file.toString());

    boolean before = recording.diverged();
    recording.diverge("ABC-1", "ship", "the turn never opened");

    assertThat(before).isFalse();
    assertThat(recording.diverged()).isTrue();
  }

  @Test
  void logsOnlyTheFirstDivergenceBecauseLaterOneFollowsFromIt(@TempDir Path root)
      throws Exception {
    Path file = root.resolve("recording.jsonl");
    Files.writeString(file, "\n");
    Recording recording = new Recording(file.toString());
    try (FakeLogCapture log = new FakeLogCapture(Recording.class)) {
      recording.diverge("ABC-1", "ship", "first cause");
      recording.diverge("ABC-1", "deploy", "second cause");

      assertThat(log.events()).hasSize(1);
      assertThat(log.events().getFirst().getKeyValuePairs())
          .anyMatch(pair -> pair.key.equals("cause") && pair.value.equals("first cause"));
    }
  }
}

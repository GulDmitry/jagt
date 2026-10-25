package dev.jagt.orchestrator.adapter.replay;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;

class RecordingTest {

    @Test
    void readsBackTheLinesAWriteRecorded(@TempDir Path root) {
        Path file = root.resolve("recording.jsonl");
        List<RecordingLine> lines = List.of(new RecordingLine.Opened("ABC-1", 1, "ship"),
                new RecordingLine.Wrote("ABC-1", 1, Map.of("a.txt", "done")));

        Recording.write(file, lines);

        assertThat(Recording.read(file)).isEqualTo(lines);
    }

    @Test
    void startsReplayingAsNotDivergedAndIsDivergedOnceAnythingDiverges(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, "\n");
        Recording recording = new Recording(file.toString());

        boolean before = recording.diverged();
        recording.diverge("ABC-1", "ship", "the turn never opened");

        assertThat(before).isFalse();
        assertThat(recording.diverged()).isTrue();
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void logsOnlyTheFirstDivergenceBecauseALaterOneFollowsFromIt(@TempDir Path root) throws Exception {
        Path file = root.resolve("recording.jsonl");
        Files.writeString(file, "\n");
        Recording recording = new Recording(file.toString());
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger recordingLog = (Logger) LoggerFactory.getLogger(Recording.class);
        recordingLog.addAppender(log);

        recording.diverge("ABC-1", "ship", "first cause");
        recording.diverge("ABC-1", "deploy", "second cause");

        recordingLog.detachAppender(log);
        assertThat(log.list).hasSize(1);
        assertThat(log.list.getFirst().getKeyValuePairs()).anyMatch(pair -> pair.key.equals("cause")
                && pair.value.equals("first cause"));
    }
}

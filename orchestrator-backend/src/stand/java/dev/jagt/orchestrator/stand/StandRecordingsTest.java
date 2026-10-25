package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandRecordingsTest {

    @Test
    void theRecordingsOfTheOldestRunsFoldAwayWholePastTheCeiling(@TempDir Path dir) throws Exception {
        IntStream.rangeClosed(1, StandRecordings.KEPT + 1).forEach(pid -> {
            try {
                for (String scenario : List.of("happy-path-act", "plan-judge")) {
                    Path file = Files.writeString(dir.resolve(scenario + "-jagt-stand-" + pid + "-3.jsonl"), "\n");
                    Files.setLastModifiedTime(file, FileTime.fromMillis(pid * 1000L));
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });

        StandRecordings.fold(dir);

        assertThat(dir).isDirectoryNotContaining(path -> path.getFileName().toString().contains("-stand-1-"))
                .isDirectoryContaining(path -> path.getFileName().toString().contains("-stand-2-"));
        assertThat(Files.list(dir).count()).isEqualTo(StandRecordings.KEPT * 2L);
    }

    @Test
    void leavesAFileThatIsNoRecordingOfARun(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("notes.txt"), "kept\n");
        IntStream.rangeClosed(1, StandRecordings.KEPT + 1).forEach(pid -> StandRecordingsTest.write(dir, pid));

        StandRecordings.fold(dir);

        assertThat(dir.resolve("notes.txt")).exists();
    }

    private static void write(Path dir, int pid) {
        try {
            Files.writeString(dir.resolve("plan-act-jagt-stand-" + pid + "-1.jsonl"), "\n");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}

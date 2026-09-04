package dev.jagt.orchestrator.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityReportTest {

    @Test
    void showsTheNewestTaskEntriesFirst(@TempDir Path root) throws IOException {
        Path log = root.resolve("jagt.log");
        Files.writeString(log, """
                {"@timestamp":"2026-08-18T08:00:00Z","message":"sweep ABC-1: 2 thread(s) relayed","task":"ABC-1","alias":"a1"}
                {"@timestamp":"2026-08-18T09:00:00Z","message":"auto-review poll found nothing","task":"ABC-2","alias":null}
                """);

        String report = new ActivityReport(log.toString(), new JsonMapper()).render();

        assertThat(report).containsSubsequence("auto-review poll found nothing", "2 thread(s) relayed");
        assertThat(report).contains("a1", "ABC-2");
    }

    @Test
    void rendersTheFieldsBecauseTheEventAloneNamesNoRequest(@TempDir Path root) throws IOException {
        Path log = root.resolve("jagt.log");
        Files.writeString(log, """
                {"@timestamp":"2026-08-18T08:00:00Z","message":"sweep done","task":"ABC-1","alias":"a1",\
                "outcome":"UNREADABLE","said":"error: read failed"}
                """);

        String report = new ActivityReport(log.toString(), new JsonMapper()).render();

        assertThat(report).contains("sweep done outcome=UNREADABLE said=error: read failed");
    }

    @Test
    void leavesOutEntriesThatNameNoTask(@TempDir Path root) throws IOException {
        Path log = root.resolve("jagt.log");
        Files.writeString(log, """
                {"@timestamp":"2026-08-18T08:00:00Z","message":"Board serving on http://localhost:8290"}
                {"@timestamp":"2026-08-18T08:00:01Z","message":"relayed a brief","task":"ABC-1"}
                """);

        String report = new ActivityReport(log.toString(), new JsonMapper()).render();

        assertThat(report).contains("relayed a brief").doesNotContain("Board serving");
    }

    @Test
    void saysTheLogIsNotStructuredInsteadOfShowingNothing(@TempDir Path root) throws IOException {
        Path log = root.resolve("plain.log");
        Files.writeString(log, "08:00:00 INFO  sweep ABC-1: relayed 2 comments\n");

        String report = new ActivityReport(log.toString(), new JsonMapper()).render();

        assertThat(report).contains("not structured JSON");
    }

    @Test
    void saysTheFormatChangedWhenTheNEWESTLineIsNoLongerJson(@TempDir Path root) throws IOException {
        Path log = root.resolve("switched.log");
        Files.writeString(log, """
                {"@timestamp":"2026-08-18T08:00:00Z","message":"sweep ABC-1: relayed","task":"ABC-1"}
                08:30:00 INFO  the format was blanked after this line
                """);

        String report = new ActivityReport(log.toString(), new JsonMapper()).render();

        assertThat(report).contains("not structured JSON").doesNotContain("sweep ABC-1");
    }

    @Test
    void saysOlderEntriesRolledOverInsteadOfLookingLikeJagtDidNothing(@TempDir Path root) throws IOException {
        Path log = root.resolve("jagt.log");
        Files.writeString(log, """
                {"@timestamp":"2026-08-18T08:00:00Z","message":"Board serving on http://localhost:8290"}
                """);
        Files.writeString(root.resolve("jagt.log.2026-08-17.0.gz"), "rotated");

        String report = new ActivityReport(log.toString(), new JsonMapper()).render();

        assertThat(report).contains("rolled over", "1 archived file(s)");
    }

    @Test
    void saysWhereTheRecordWouldLiveWhenNoLogFileIsConfigured() {
        String report = new ActivityReport("", new JsonMapper()).render();

        assertThat(report).contains("logging.file.name");
    }
}

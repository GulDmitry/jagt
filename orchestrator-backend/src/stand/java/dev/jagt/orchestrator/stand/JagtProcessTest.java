package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JagtProcessTest {

    @Test
    void theFirstJagtsOutputAndBackendLogAreKeptBesideTheSecondsBeforeItStarts(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("jagt.out"), "first out");
        Files.writeString(root.resolve("jagt-backend.log"), "first log");

        JagtProcess.keepOutput(root);

        assertThat(root.resolve("jagt.out")).doesNotExist();
        assertThat(root.resolve("jagt-backend.log")).doesNotExist();
        assertThat(root.resolve("jagt.out.before-restart")).hasContent("first out");
        assertThat(root.resolve("jagt-backend.log.before-restart")).hasContent("first log");
    }

    @Test
    void keepsNothingWhenTheFirstJagtWroteNoBackendLog(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("jagt.out"), "first out");

        JagtProcess.keepOutput(root);

        assertThat(root.resolve("jagt-backend.log.before-restart")).doesNotExist();
    }

    @Test
    void theEvidenceOfAFailedRunHoldsBothJagtsOutput(@TempDir Path dir) throws Exception {
        Path root = Files.createDirectories(dir.resolve("run"));
        Files.writeString(root.resolve("jagt.out.before-restart"), "first out");
        Files.writeString(root.resolve("jagt.out"), "second out");

        Path kept = StandEvidence.keep(root, "mutation-restart-mid-turn/judge", dir.resolve("failed"));

        assertThat(kept.resolve("jagt.out.before-restart")).hasContent("first out");
        assertThat(kept.resolve("jagt.out")).hasContent("second out");
    }
}

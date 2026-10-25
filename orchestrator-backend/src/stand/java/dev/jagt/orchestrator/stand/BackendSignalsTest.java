package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackendSignalsTest {

    @Test
    void countsTheMasterLinesOfTheBackendLog(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("jagt-backend.log");
        Files.writeString(log, """
                {"message":"master review asked","task":"ABC-5"}
                {"message":"sweep done","task":"ABC-5"}
                {"message":"master presses","task":"ABC-5"}
                """);

        BackendSignals signals = BackendSignals.read(log);

        assertThat(signals.masterReads()).isEqualTo(2);
    }

    @Test
    void readsNothingFromALogNotWrittenYet(@TempDir Path dir) {
        BackendSignals signals = BackendSignals.read(dir.resolve("jagt-backend.log"));

        assertThat(signals.masterReads()).isZero();
    }
}

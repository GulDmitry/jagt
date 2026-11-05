package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AliveStampTest {

    @Test
    void theNextStartReadsTheLastStampThisProcessWrote(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        new AliveStamp(paths, Clock.fixed(Instant.parse("2026-11-05T07:00:00Z"), ZoneOffset.UTC)).run();

        long lastAlive = new AliveStamp(paths, Clock.systemUTC()).lastAliveBeforeStart();

        assertThat(lastAlive).isEqualTo(1793862000000L);
    }

    @Test
    void anUnreadableStampReadsAsNoneLeft(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("alive"), "half-writ");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));

        long lastAlive = new AliveStamp(paths, Clock.systemUTC()).lastAliveBeforeStart();

        assertThat(lastAlive).isZero();
    }
}

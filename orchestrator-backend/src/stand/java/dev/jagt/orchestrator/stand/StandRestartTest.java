package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StandRestartTest {

    @Test
    void aRestartedJagtBindsTheFirstJagtsPortAndTheMarkerKeepsIt() throws Exception {
        Path recording = Path.of(StandRestartTest.class.getResource("/recordings/happy-path.jsonl").toURI());
        try (StandRun stand = StandRun.replay("restart-keeps-port", StandMode.ACT, recording)) {
            StandMarker first = StandMarker.read(stand.root(), 0);

            stand.restartJagt();

            StandMarker second = StandMarker.read(stand.root(), 0);
            assertThat(second.jagtPid()).isNotEqualTo(first.jagtPid());
            assertThat(second.port()).isEqualTo(first.port());
            stand.passed();
        }
    }
}

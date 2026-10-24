package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReplayStandTest {

    @Test
    void aRecordedTicketReplaysOntoTheDeployBranchWithinTheFrame() throws Exception {
        Path recording = Path.of(ReplayStandTest.class.getResource("/recordings/happy-path.jsonl").toURI());
        try (StandRun stand = StandRun.replay("replay-happy-path", StandMode.ACT, recording)) {

            StandOutcome outcome = stand.drive(Script.to("ABC-1", "DEPLOYED").within(Duration.ofMinutes(5)));

            assertThat(stand.replayLog().divergences()).as(outcome.describe()).isEmpty();
            assertThat(stand.replayLog().drained()).as(outcome.describe()).isTrue();
            assertThat(outcome.status()).as(outcome.describe()).isEqualTo("DEPLOYED");
            assertThat(stand.project().deployBranchFile("greet.sh")).as(outcome.describe()).contains("World");
            assertThat(stand.violations()).as(outcome.describe()).isEmpty();
            stand.passed();
        }
    }

    @Test
    void endsAsADivergenceNamingTheStepARecordingNeverAnswered() throws Exception {
        Path recording = Path.of(ReplayStandTest.class.getResource("/recordings/happy-path-skips-ship.jsonl").toURI());
        try (StandRun stand = StandRun.replay("replay-skips-ship", StandMode.ACT, recording)) {

            StandOutcome outcome = stand.drive(Script.to("ABC-1", "DEPLOYED").within(Duration.ofMinutes(5)));

            assertThat(stand.replayLog().divergences()).as(outcome.describe()).singleElement()
                    .satisfies(divergence -> assertThat(divergence).startsWith("ship: "));
            stand.passed();
        }
    }
}

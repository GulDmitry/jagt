package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.stand.StandOutcome.Ending;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandOutcomeTest {

    @Test
    void aSuspendedHostOutranksTheStallItLeftBehind() {
        assertThat(Ending.of(false, true, true, false, false, true)).isEqualTo(Ending.SUSPENDED);
    }

    @Test
    void theStatusReachedOutranksASuspensionAfterIt() {
        assertThat(Ending.of(true, true, true, false, false, false)).isEqualTo(Ending.REACHED);
    }

    @Test
    void aStoppedTaskEndsAsStoppedNotAsAStallOrATimeout() {
        assertThat(Ending.of(false, false, true, false, false, true)).isEqualTo(Ending.STOPPED);
    }

    @Test
    void aQuietRunStalls() {
        assertThat(Ending.of(false, false, false, false, false, true)).isEqualTo(Ending.STALLED);
    }

    @Test
    void aStoppedRunDescribesItselfWithTheStopsTriggerAndEvidence() {
        StandOutcome outcome = new StandOutcome("IN_PROGRESS", "YOU", Duration.ofSeconds(90), List.of(), Path.of("/stand"),
                Ending.STOPPED, "MISS: a push to main was refused", "MISS", "PLAIN", 0);

        assertThat(outcome.describe()).startsWith("stopped, human needed (MISS: a push to main was refused): last status");
    }

    @Test
    void aRunWaitingOnTheReviewerEndsAsSuchBeforeItCountsAsStalled() {
        assertThat(Ending.of(false, false, false, false, true, true)).isEqualTo(Ending.WAITING_ON_THE_REVIEWER);
    }
}

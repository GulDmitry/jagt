package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.stand.StandOutcome.Ending;
import org.junit.jupiter.api.Test;

class StandOutcomeTest {

    @Test
    void aSuspendedHostOutranksTheStallItLeftBehind() {
        assertThat(Ending.of(false, true, false, true)).isEqualTo(Ending.SUSPENDED);
    }

    @Test
    void theStatusReachedOutranksASuspensionAfterIt() {
        assertThat(Ending.of(true, true, false, false)).isEqualTo(Ending.REACHED);
    }

    @Test
    void aQuietRunStalls() {
        assertThat(Ending.of(false, false, false, true)).isEqualTo(Ending.STALLED);
    }
}

package dev.jagt.orchestrator.capability.trust;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.service.TrustedGitState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TrustCapabilityTest {

    private final TrustedGitState trusted = mock(TrustedGitState.class);

    @Test
    void trustsTheGitStateOfTheTaskAsItStandsAndSaysSo() {
        Outcome outcome = new TrustCapability(trusted).run("ABC-1");

        verify(trusted).trust("ABC-1");
        assertThat(outcome.message()).isEqualTo("trust ABC-1: its git config and hooks are trusted as they are now");
    }
}

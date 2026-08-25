package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionHooksTest {

    /** A CLI with no hooks of its own must still be a supported runtime, not a startup failure. */
    @Test
    void mapsNothingForARuntimeThatDeclaresNoEvents() {
        assertThat(SessionHooks.of("a-cli-that-ships-no-hooks")).isEmpty();
    }

    /**
     * A value the endpoint does not know is refused, and the hook discards the answer — so a typo here reports
     * nothing for the life of an install with no symptom anywhere.
     */
    /**
     * The one producer of a blocked session anywhere. Dropped or misspelt, every blocked session reports quiet
     * for the life of an install, with no exception and no symptom but a board that stops turning over.
     */
    @Test
    void declaresThePhraseThatSeparatesABlockedSessionFromAQuietOne() {
        assertThat(SessionHooks.blockingNotification("claude")).isNotBlank();
    }

    @Test
    void declaresOnlyStatesTheOrchestratorAnswersTo() {
        assertThat(SessionHooks.of("claude").values()).isSubsetOf("waiting", "gone", "idle", "working");
    }
}

package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionHooksTest {

    @Test
    void mapsNothingForARuntimeThatDeclaresNoEvents() {
        assertThat(SessionHooks.of("a-cli-that-ships-no-hooks")).isEmpty();
    }

    @Test
    void declaresThePhraseThatSeparatesABlockedSessionFromAQuietOne() {
        assertThat(SessionHooks.blockingNotification("claude")).isNotBlank();
    }

    @Test
    void declaresOnlyStatesTheOrchestratorAnswersTo() {
        assertThat(SessionHooks.of("claude").values()).isSubsetOf("waiting", "gone", "idle", "working");
    }
}

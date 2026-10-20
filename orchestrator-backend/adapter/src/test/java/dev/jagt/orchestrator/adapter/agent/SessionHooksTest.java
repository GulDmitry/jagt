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

    @Test
    void namesTheErrorWordThatMeansAUsageLimit() {
        assertThat(SessionHooks.rateLimitError("claude")).isEqualTo("rate_limit");
    }

    @Test
    void namesTheEndReasonsANewStartFollowsAtOnce() {
        assertThat(SessionHooks.continuingEnds("claude")).containsExactlyInAnyOrder("clear", "resume");
    }

    @Test
    void namesTheStartThatFollowsACompaction() {
        assertThat(SessionHooks.compactedStart("claude")).isEqualTo("compact");
    }

    @Test
    void saysNothingForARuntimeThatShipsNoHooks() {
        assertThat(SessionHooks.rateLimitError("a-cli-that-ships-no-hooks")).isEmpty();
        assertThat(SessionHooks.continuingEnds("a-cli-that-ships-no-hooks")).isEmpty();
        assertThat(SessionHooks.compactedStart("a-cli-that-ships-no-hooks")).isEmpty();
    }
}

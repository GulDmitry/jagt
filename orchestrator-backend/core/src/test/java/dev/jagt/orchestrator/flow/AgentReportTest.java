package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AgentReportTest {

    @ParameterizedTest
    @ValueSource(strings = {"no_changes: nitpick already answered",
            "outcome=no_changes: withdrawn thread relayed again",
            "outcome: no_changes the fixture has no hits"})
    void readsARoundThatChangedNothingWhenTheAgentNamedTheOutcomeInTheMessage(String message) {
        assertThat(AgentReport.of(message)).isEqualTo(AgentReport.NO_CHANGES);
    }

    @Test
    void readsAStoppedAgentWhenItNamedTheQuestionOutcomeInTheMessage() {
        assertThat(AgentReport.of("outcome=question: retitle the request or override the check"))
                .isEqualTo(AgentReport.QUESTION);
    }

    @Test
    void keepsAProseSentenceThatOnlyStartsLikeAMarkerAsPlain() {
        assertThat(AgentReport.of("no changesets were needed")).isEqualTo(AgentReport.PLAIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"outcome=no_changes: withdrawn thread relayed again",
            "outcome: no_changes \u2014 withdrawn thread relayed again"})
    void leavesTheLineWithoutTheOutcomeTheAgentTypedIntoIt(String message) {
        assertThat(AgentReport.withoutMarker(message)).isEqualTo("withdrawn thread relayed again");
    }
}

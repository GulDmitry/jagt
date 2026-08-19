package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.codehost.CodeHost;
import dev.jagt.orchestrator.config.CodeHostProperties;
import dev.jagt.orchestrator.config.TrackerProperties;
import dev.jagt.orchestrator.tracker.Tracker;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

class OutsideReadsCheckTest {

    @Test
    void staysQuietWhenNeitherOutsideSystemIsWired() {
        assertThat(new OutsideReadsCheck(CodeHostProperties.none(), List.of(), TrackerProperties.none(),
                List.of()).problems()).isEmpty();
    }

    @Test
    void refusesAHostTypeNoImplementationAnswersTo() {
        CodeHostProperties configured = new CodeHostProperties("gitlabb", "https://code.example", "token");

        assertThat(new OutsideReadsCheck(configured, List.of(), TrackerProperties.none(), List.of()).problems())
                .singleElement(STRING).contains("orchestrator.code-host.type=gitlabb selects nothing");
    }

    @Test
    void refusesAWiredHostWithNoTokenBecauseEveryReadWouldSilentlyCostAModelCall() {
        CodeHostProperties configured = new CodeHostProperties("gitlab", "https://code.example", " ");

        assertThat(new OutsideReadsCheck(configured, List.of(Mockito.mock(CodeHost.class)),
                TrackerProperties.none(), List.of()).problems())
                .singleElement(STRING).contains("orchestrator.code-host.token");
    }

    @Test
    void refusesATrackerWhoseBaseUrlNothingCouldBeMatchedAgainst() {
        TrackerProperties configured = new TrackerProperties("jira", "tracker.example", null, "token");

        assertThat(new OutsideReadsCheck(CodeHostProperties.none(), List.of(), configured,
                List.of(Mockito.mock(Tracker.class))).problems())
                .singleElement(STRING).contains("orchestrator.tracker.base-url", "is not an http(s) URL");
    }

    @Test
    void namesBothHalvesOfAWiredTrackerThatHasNeither() {
        TrackerProperties configured = new TrackerProperties("jira", null, null, null);

        assertThat(new OutsideReadsCheck(CodeHostProperties.none(), List.of(), configured,
                List.of(Mockito.mock(Tracker.class))).problems())
                .hasSize(2)
                .anySatisfy(problem -> assertThat(problem).contains("orchestrator.tracker.base-url"))
                .anySatisfy(problem -> assertThat(problem).contains("orchestrator.tracker.token"));
    }
}

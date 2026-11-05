package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.ReachLedger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeReachTest {

    @Test
    void boardHeadNamesTheEndpointWhenItsModelsDoNotAnswer() {
        ReachLedger reach = new ReachLedger();

        new OpenCodeReach(new OpenCodeProperties("", "http://127.0.0.1:8080/v1", "qwen"),
                url -> !url.equals("http://127.0.0.1:8080/v1/models"), reach).run();

        assertThat(reach.standing()).extracting(ReachLost::said)
                .containsExactly("opencode cannot reach http://127.0.0.1:8080/v1");
    }

    @Test
    void lostReachEndsWhenTheEndpointAnswersAgain() {
        ReachLedger reach = new ReachLedger();
        reach.lost(new ReachLost("opencode", "http://127.0.0.1:8080/v1", null, "opencode cannot reach it"));

        new OpenCodeReach(new OpenCodeProperties("", "http://127.0.0.1:8080/v1", "qwen"),
                url -> url.equals("http://127.0.0.1:8080/v1/models"), reach).run();

        assertThat(reach.standing()).isEmpty();
    }
}

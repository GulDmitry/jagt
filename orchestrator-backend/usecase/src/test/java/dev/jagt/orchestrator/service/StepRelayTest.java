package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StepRelayTest {

    private final AgentSessions sessions = mock(AgentSessions.class);

    @Test
    void tellsTheSessionTheStepsFactsThenWhatTheBriefAsks(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n\nopen it in a browser\n");
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions);

        relay.relay("ABC-42", "deploy", "ABC-42 is deployed:\n- proj: 01234567 on dev");

        verify(sessions).relayIfChanged("ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev\n\nopen it in a browser");
    }

    @Test
    void tellsTheFactsAloneAtAStepTheBriefLeavesEmpty(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n");
        StepRelay relay = new StepRelay(new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))), sessions);

        relay.relay("ABC-42", "deploy", "ABC-42 is deployed:\n- proj: 01234567 on dev");

        verify(sessions).relayIfChanged("ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev");
    }
}

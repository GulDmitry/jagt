package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HookEndpointTest {

    @Test
    void namesTheWorktreeSoAReportIdentifiesItsTaskWithoutReadingAnyPayload() {
        String command = new HookEndpoint("http://127.0.0.1:8290/api/agent/session")
                .command(Path.of("/wt/ABC-1-proj"), "waiting");

        assertThat(command).contains("-H 'X-Working-Directory: /wt/ABC-1-proj'",
                "'http://127.0.0.1:8290/api/agent/session/waiting'");
    }

    /** A failure reported here would put jagt's own plumbing in front of the human working in that session. */
    @Test
    void reportsNothingBackToTheSessionWhenTheBackendIsNotListening() {
        String command = new HookEndpoint("http://127.0.0.1:8290/api/agent/session")
                .command(Path.of("/wt/ABC-1-proj"), "gone");

        assertThat(command).endsWith("|| true");
    }
}

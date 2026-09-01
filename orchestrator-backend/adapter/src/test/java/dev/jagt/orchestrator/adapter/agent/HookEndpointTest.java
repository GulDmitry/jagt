package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HookEndpointTest {

    @Test
    void namesTheWorktreeSoAReportIdentifiesItsTaskWithoutReadingAnyPayload() {
        String command = new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent")
                .command(Path.of("/wt/ABC-1-proj"), "waiting");

        assertThat(command).contains("-H 'X-Working-Directory: /wt/ABC-1-proj'",
                "'http://127.0.0.1:8290/api/agent/session/waiting'");
    }

    @Test
    void reportsNothingBackToTheSessionWhenTheBackendIsNotListening() {
        String command = new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent")
                .command(Path.of("/wt/ABC-1-proj"), "gone");

        assertThat(command).endsWith("|| true");
    }
}

package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HostCliTest {

    @Test
    void startupRefusesASelectedCliMissingFromPathNamingTheKey() {
        HostCli cli = new HostCli(mock(Processes.class), "no-such-host-cli");

        assertThat(cli.missing("orchestrator.codeHost")).extracting(Problem::key)
                .containsExactly("orchestrator.codeHost");
    }
}

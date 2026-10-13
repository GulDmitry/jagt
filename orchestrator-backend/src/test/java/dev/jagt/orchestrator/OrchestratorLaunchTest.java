package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorLaunchTest {

    @Test
    void passesANamedConfigFileOnceSoSpringDoesNotJoinItWithItself() {
        String[] launched = OrchestratorApplication.withConfigFile(new String[]{"--orchestrator.config-file=/opt/jagt.yml"});

        assertThat(launched).filteredOn(arg -> arg.startsWith("--orchestrator.config-file="))
                .containsExactly("--orchestrator.config-file=/opt/jagt.yml");
    }
}

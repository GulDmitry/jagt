package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeSandboxCheckTest {

    @Test
    void refusesToStartOnLinuxWhenTheSandboxLacksItsRelay() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults());

        List<Problem> problems = new ClaudeSandboxCheck(OrchestratorProperties.defaults().withPlatform("linux"),
                config, "bwrap"::equals).problems();

        assertThat(problems).extracting(Problem::key).containsExactly("orchestrator.agent.sandbox.enabled");
    }

    @Test
    void needsNothingInstalledOnMacos() {
        List<Problem> problems = new ClaudeSandboxCheck(OrchestratorProperties.defaults().withPlatform("macos"),
                mock(ConfigService.class), name -> false).problems();

        assertThat(problems).isEmpty();
    }
}

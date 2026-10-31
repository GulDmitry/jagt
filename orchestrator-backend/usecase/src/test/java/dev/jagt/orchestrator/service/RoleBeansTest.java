package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.port.AgentRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleBeansTest {

    @Test
    void qaJobsStandIdleWhenNoQaRoleIsInstalledEvenWhereJagtYmlTurnsItOn(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    qa:\n      enabled: true\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
        ConfigService config = new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class));
        RoleBeans beans = new RoleBeans();

        RoleDefinition qa = beans.qaDefinition(new InstalledRoles(paths));

        assertThat(beans.qaSettings(qa, config).load().enabledOrDefault()).isFalse();
    }
}

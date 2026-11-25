package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestConfigServiceBuilder.configService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigReachTest {

  @TempDir Path root;

  @Test
  void reachesTheDirectoriesSessionMayWriteThatExistWithTheSandboxOff() throws Exception {
    Path specs = Files.createDirectory(root.resolve("specs"));
    ConfigService config =
        configService()
            .withRoot(root)
            .withYaml(
                """
                orchestrator:
                  agent:
                    sandbox:
                      enabled: false
                      allowWrite: ["%s", "%s"]
                """
                    .formatted(specs, root.resolve("gone")))
            .build(mock(AgentRuntime.class));

    var beside = new ConfigReach(config, FakeHost.machine()).beside();

    assertThat(beside).containsExactly(specs.toRealPath());
  }

  @Test
  void reachesTheHostsHomeWhereTheConfigNamesItByTilde() throws Exception {
    Path home = Files.createDirectory(root.resolve("home"));
    ConfigService config =
        configService()
            .withRoot(root)
            .withYaml(
                """
                orchestrator:
                  agent:
                    sandbox:
                      enabled: false
                      allowWrite: ["~"]
                """)
            .build(mock(AgentRuntime.class));

    var beside = new ConfigReach(config, FakeHost.machine().withHome(home)).beside();

    assertThat(beside).containsExactly(home.toRealPath());
  }
}

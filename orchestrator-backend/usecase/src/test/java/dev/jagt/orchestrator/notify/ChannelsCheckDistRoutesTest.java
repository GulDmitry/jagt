package dev.jagt.orchestrator.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.Points;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.ChannelDriver;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.InstalledRoles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

class ChannelsCheckDistRoutesTest {

  @Test
  void shippedExampleRoutesTodaysBannersAndPassesTheStartCheck() {
    Path dist = Path.of("../../jagt.yml.dist").toAbsolutePath().normalize();
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withConfigFile(dist.toString()));
    ConfigService config =
        new ConfigService(
            paths,
            mock(AgentRuntime.class),
            mock(ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn())));
    InstalledRoles roles = mock(InstalledRoles.class);
    when(roles.points()).thenReturn(Points.builtIn());
    ChannelDriver desktop = mock(ChannelDriver.class);
    when(desktop.kind()).thenReturn("desktop");
    ChannelsCheck check =
        new ChannelsCheck(config, roles, List.of(desktop), new StandardEnvironment(), paths);

    assertThat(check.problems()).isEmpty();
    assertThat(check.notes()).isEmpty();
    assertThat(config.load().notifications().routeDefs().keySet())
        .containsExactlyInAnyOrder(
            "your-move",
            "stopped",
            "auto-review",
            "conflict-stalled",
            "request-opened",
            "housekeeping",
            "install",
            "usage");
  }

  @Test
  void shippedExampleShowsCommentedTeamsChannel() throws Exception {
    Path dist = Path.of("../../jagt.yml.dist").toAbsolutePath().normalize();

    assertThat(Files.readAllLines(dist))
        .anyMatch(line -> line.matches("\\s*# chat: \\{ kind: teams, webhookEnv: \\w+ \\}.*"));
  }
}

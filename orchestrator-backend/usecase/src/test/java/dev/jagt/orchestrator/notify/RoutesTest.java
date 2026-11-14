package dev.jagt.orchestrator.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.notify.Routes.Routed;
import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig.ChannelDef;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig.RouteDef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutesTest {

  private final ConfigService config = mock(ConfigService.class);
  private final Routes routes = new Routes(config);

  @Test
  void routesOnePointToTwoChannelsEachWithItsOwnText() {
    NotificationsConfig notifications =
        new NotificationsConfig(
            Map.of(
                "desktop", new ChannelDef("desktop", null, null),
                "team", new ChannelDef("teams", "TEAMS_HOOK", null)),
            Map.of(
                "deployed",
                List.of(
                    new RouteDef("desktop", "jagt · {task}", "{sha}"),
                    new RouteDef("team", null, "{alias} is live"))));

    when(config.load()).thenReturn(ConfigFile.defaults().withNotifications(notifications));

    List<Routed> routed = routes.of("deployed");

    assertThat(routed)
        .containsExactly(
            new Routed(
                new ChannelAddress("desktop", "desktop", "", List.of()), "jagt · {task}", "{sha}"),
            new Routed(
                new ChannelAddress("team", "teams", "TEAMS_HOOK", List.of()),
                "",
                "{alias} is live"));
  }

  @Test
  void routesPointToNobodyWhenNoRouteNamesIt() {
    NotificationsConfig notifications =
        new NotificationsConfig(
            Map.of("desktop", new ChannelDef("desktop", null, null)),
            Map.of("deployed", List.of(new RouteDef("desktop", "t", "x"))));

    when(config.load()).thenReturn(ConfigFile.defaults().withNotifications(notifications));

    assertThat(routes.of("merged")).isEmpty();
  }

  @Test
  void skipsRouteNamingChannelThatIsNotDeclared() {
    NotificationsConfig notifications =
        new NotificationsConfig(
            Map.of(), Map.of("deployed", List.of(new RouteDef("ghost", "t", "x"))));

    when(config.load()).thenReturn(ConfigFile.defaults().withNotifications(notifications));

    assertThat(routes.of("deployed")).isEmpty();
  }
}

package dev.jagt.orchestrator.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.flow.role.Points;
import dev.jagt.orchestrator.port.channel.ChannelDriver;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig.ChannelDef;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig.RouteDef;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import dev.jagt.orchestrator.service.InstalledRoles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class ChannelsCheckTest {

  private final ConfigService config = mock(ConfigService.class);
  private final InstalledRoles roles = mock(InstalledRoles.class);
  private final ChannelDriver desktop = mock(ChannelDriver.class);
  private final ChannelDriver command = mock(ChannelDriver.class);
  private final ChannelDriver teams = mock(ChannelDriver.class);
  private final StandardEnvironment environment = new StandardEnvironment();
  private final OrchestratorPaths paths =
      new OrchestratorPaths(
          OrchestratorProperties.defaults()
              .withConfigFile(Path.of("build.gradle").toAbsolutePath().toString()));

  private ChannelsCheck check;

  @BeforeEach
  void checkOverTheThreeDrivers() {
    when(desktop.kind()).thenReturn("desktop");
    when(command.kind()).thenReturn("command");
    when(teams.kind()).thenReturn("teams");
    when(teams.needsSecret()).thenReturn(true);
    when(roles.points()).thenReturn(Points.builtIn());
    check = new ChannelsCheck(config, roles, List.of(desktop, command, teams), environment, paths);
  }

  @Test
  void findsNothingWrongWithRoutesToDeclaredChannelsOfKnownPoints() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("desktop", new ChannelDef("desktop", null, null)),
                        Map.of(
                            "deployed",
                            List.of(
                                new RouteDef(
                                    "desktop", "jagt · {alias}", "{sha} {pipeline} {board}"))))));

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesRouteNamingPointNoCatalogueHas() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("desktop", new ChannelDef("desktop", null, null)),
                        Map.of("deployd", List.of(new RouteDef("desktop", "t", "x"))))));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.notifications.points.deployd");
  }

  @Test
  void refusesRouteNamingChannelNotDeclared() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of(), Map.of("deployed", List.of(new RouteDef("team", "t", "x"))))));

    assertThat(check.problems())
        .extracting(Problem::text)
        .singleElement()
        .asString()
        .contains("orchestrator.notifications.points.deployed")
        .contains("'team'");
  }

  @Test
  void refusesChannelOfKindNoDriverTells() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("chat", new ChannelDef("pager", null, null)), Map.of())));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.notifications.channels.chat.kind");
  }

  @Test
  void acceptsTeamsChannelWhoseWebhookEnvIsSet() {
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("test", Map.of("ABC_HOOK", "https://example.invalid")));
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("chat", new ChannelDef("teams", "ABC_HOOK", null)), Map.of())));

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesTeamsChannelNamingNoWebhookEnv() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("chat", new ChannelDef("teams", null, null)), Map.of())));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.notifications.channels.chat.webhookEnv");
  }

  @Test
  void refusesCommandChannelWithNoArgv() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("ops", new ChannelDef("command", null, List.of())), Map.of())));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.notifications.channels.ops.command");
  }

  @Test
  void refusesChannelWhoseWebhookEnvIsNotSet() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("ops", new ChannelDef("command", "ABC_HOOK", List.of("cat"))),
                        Map.of())));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.notifications.channels.ops.webhookEnv");
  }

  @Test
  void acceptsChannelWhoseWebhookEnvIsSet() {
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("test", Map.of("ABC_HOOK", "https://example.invalid")));
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("ops", new ChannelDef("command", "ABC_HOOK", List.of("cat"))),
                        Map.of())));

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesRouteWithNoText() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("desktop", new ChannelDef("desktop", null, null)),
                        Map.of("deployed", List.of(new RouteDef("desktop", "t", " "))))));

    assertThat(check.problems())
        .extracting(Problem::text)
        .singleElement()
        .asString()
        .contains("no text");
  }

  @Test
  void refusesUnknownPlaceholderNamingIt() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("desktop", new ChannelDef("desktop", null, null)),
                        Map.of("deployed", List.of(new RouteDef("desktop", "t", "{shaa}"))))));

    assertThat(check.problems())
        .extracting(Problem::text)
        .singleElement()
        .asString()
        .contains("{shaa}");
  }

  @Test
  void refusesTaskPlaceholderOnPointStandingFromNoTask() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("desktop", new ChannelDef("desktop", null, null)),
                        Map.of("usage", List.of(new RouteDef("desktop", "t", "{task} {body}"))))));
    when(roles.points()).thenReturn(List.of(Point.USAGE));

    assertThat(check.problems())
        .extracting(Problem::text)
        .singleElement()
        .asString()
        .contains("{task}")
        .contains("usage");
  }

  @Test
  void reportsEveryProblemAtOnce() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("chat", new ChannelDef("pager", null, null)),
                        Map.of(
                            "deployd", List.of(new RouteDef("ghost", "t", "{nope}")),
                            "deployed", List.of(new RouteDef("chat", "t", ""))))));

    assertThat(check.problems()).hasSizeGreaterThanOrEqualTo(5);
  }

  @Test
  void warnsOnceForRouteNamingPointOfDisabledRole() {
    RoleDefinition acceptance = mock(RoleDefinition.class);
    when(acceptance.id()).thenReturn("acceptance");
    List<Point> all = new ArrayList<>(Points.builtIn());
    all.add(new Point("acceptance-passed", "the role passed", false));
    when(roles.points()).thenReturn(all);
    when(roles.definitions()).thenReturn(List.of(acceptance));
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("desktop", new ChannelDef("desktop", null, null)),
                        Map.of("acceptance-passed", List.of(new RouteDef("desktop", "t", "x")))))
                .withRoles(Map.of("acceptance", new RoleConfig(false, null))));
    when(desktop.kind()).thenReturn("desktop");
    ChannelsCheck check = new ChannelsCheck(config, roles, List.of(desktop), environment, paths);

    assertThat(check.problems()).isEmpty();
    assertThat(check.notes())
        .extracting(Problem::text)
        .singleElement()
        .asString()
        .contains("acceptance")
        .contains("disabled");
  }

  @Test
  void notesOnceThatNothingWillBeToldWhereNoNotificationsBlockIsWritten() {
    when(roles.points()).thenReturn(Points.builtIn());
    when(config.load()).thenReturn(ConfigFile.defaults());
    ChannelsCheck check = new ChannelsCheck(config, roles, List.of(desktop), environment, paths);

    assertThat(check.problems()).isEmpty();
    assertThat(check.notes())
        .extracting(Problem::text)
        .singleElement()
        .asString()
        .contains("nothing will be told");
  }

  @Test
  void notesNothingWhereOneRouteIsWritten() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withNotifications(
                    new NotificationsConfig(
                        Map.of("desktop", new ChannelDef("desktop", null, null)),
                        Map.of("deployed", List.of(new RouteDef("desktop", "t", "x"))))));

    assertThat(check.notes()).isEmpty();
  }
}

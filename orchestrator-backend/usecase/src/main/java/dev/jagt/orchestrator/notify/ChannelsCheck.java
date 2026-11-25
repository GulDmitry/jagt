package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.flow.role.Points;
import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.port.channel.ChannelDriver;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.service.ConfigFile.NotificationsConfig;
import dev.jagt.orchestrator.service.ConfigFile.NotificationsConfig.ChannelDef;
import dev.jagt.orchestrator.service.ConfigFile.NotificationsConfig.RouteDef;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * A route or a channel in {@code jagt.yml} that nothing could tell through is refused, every
 * problem at once; a route naming the point of a disabled role is inert and said once.
 */
@Component
public class ChannelsCheck implements StartupCheck {

  private static final String NOTIFICATIONS = "orchestrator.notifications.";
  private static final String CHANNELS = NOTIFICATIONS + "channels.";
  private static final String POINTS = NOTIFICATIONS + "points.";

  private final ConfigService config;
  private final InstalledRoles roles;
  private final List<ChannelDriver> drivers;
  private final Environment environment;

  public ChannelsCheck(
      ConfigService config,
      InstalledRoles roles,
      List<ChannelDriver> drivers,
      Environment environment) {
    this.config = config;
    this.roles = roles;
    this.drivers = List.copyOf(drivers);
    this.environment = environment;
  }

  @Override
  public List<Problem> problems() {
    if (!config.fileExists()) {
      return List.of();
    }
    NotificationsConfig notifications = config.load().notifications();
    List<Problem> problems = new ArrayList<>();
    notifications.channelDefs().forEach((name, def) -> problems.addAll(channelProblems(name, def)));
    notifications
        .routeDefs()
        .forEach((id, routes) ->
            routes.forEach(route -> problems.addAll(routeProblems(notifications, id, route))));
    return problems;
  }

  @Override
  public List<Problem> notes() {
    if (!config.fileExists()) {
      return List.of();
    }
    Set<String> routed = config.load().notifications().routeDefs().keySet();
    if (routed.isEmpty()) {
      return List.of(
          new Problem(
              NOTIFICATIONS + "points",
              NOTIFICATIONS + "points routes no point, so nothing will be told"));
    }
    return routed.stream()
        .flatMap(id -> disabledRoleOf(id).stream().map(role -> inert(id, role)))
        .toList();
  }

  @Override
  public boolean machine() {
    return false;
  }

  private List<Problem> channelProblems(String name, @Nullable ChannelDef def) {
    String key = CHANNELS + name;
    String kind = def == null ? null : def.kind();
    if (def == null
        || kind == null
        || drivers.stream().noneMatch(driver -> driver.kind().equals(kind))) {
      return List.of(
          new Problem(
              key + ".kind",
              key + ".kind is '" + kind + "'; the kinds jagt can tell through are " + kinds()));
    }
    List<Problem> problems = new ArrayList<>();
    drivers.stream()
        .filter(driver -> driver.kind().equals(kind))
        .flatMap(driver -> driver.faults(addressOf(name, kind, def)).stream())
        .forEach(fault ->
            problems.add(
                new Problem(
                    key + "." + fault.setting(),
                    key + "." + fault.setting() + " " + fault.problem())));
    if (needsSecret(kind) && def.webhookEnv() == null) {
      problems.add(
          new Problem(key + ".webhookEnv", key + ".webhookEnv is missing, so nothing is posted"));
    }
    if (def.webhookEnv() != null && !isSet(def.webhookEnv())) {
      problems.add(
          new Problem(
              key + ".webhookEnv",
              key
                  + ".webhookEnv names the environment variable '"
                  + def.webhookEnv()
                  + "', which is not set"));
    }
    return problems;
  }

  private List<Problem> routeProblems(
      NotificationsConfig notifications, String id, RouteDef route) {
    String key = POINTS + id;
    List<Problem> problems = new ArrayList<>();
    Optional<Point> point = pointOf(id);
    if (point.isEmpty()) {
      problems.add(new Problem(key, key + " names no point; the points are " + pointIds()));
    }
    if (route.channel() == null || !notifications.channelDefs().containsKey(route.channel())) {
      problems.add(
          new Problem(
              key,
              key
                  + " routes to the channel '"
                  + route.channel()
                  + "', which orchestrator.notifications.channels does not declare; it declares "
                  + notifications.channelDefs().keySet()));
    }
    if (route.text() == null || route.text().isBlank()) {
      problems.add(new Problem(key, key + " routes to '" + route.channel() + "' with no text"));
    }
    problems.addAll(templateProblems(key, point, route.title()));
    problems.addAll(templateProblems(key, point, route.text()));
    return problems;
  }

  private static List<Problem> templateProblems(
      String key, Optional<Point> point, @Nullable String template) {
    if (template == null) {
      return List.of();
    }
    List<Problem> problems = new ArrayList<>();
    for (String unknown : Messages.unknownIn(template)) {
      problems.add(
          new Problem(
              key,
              key
                  + " uses the placeholder {"
                  + unknown
                  + "}; the placeholders are "
                  + Messages.PLACEHOLDERS));
    }
    if (point.filter(Point::raised).isPresent()) {
      for (String ofTask : Messages.ofTaskIn(template)) {
        problems.add(
            new Problem(
                key,
                key + " uses {" + ofTask + "}, but " + point.get().id() + " stands from no task"));
      }
    }
    return problems;
  }

  private Optional<Point> pointOf(String id) {
    return roles.points().stream().filter(point -> point.id().equals(id)).findFirst();
  }

  private Optional<String> disabledRoleOf(String pointId) {
    boolean builtIn = Points.builtIn().stream().anyMatch(point -> point.id().equals(pointId));
    if (builtIn || pointOf(pointId).isEmpty()) {
      return Optional.empty();
    }
    return roles.definitions().stream()
        .map(RoleDefinition::id)
        .filter(role -> pointId.startsWith(role + "-"))
        .filter(role -> !config.load().role(role).enabledOrDefault())
        .findFirst();
  }

  private static Problem inert(String pointId, String role) {
    return new Problem(
        POINTS + pointId, POINTS + pointId + " is never told: the role " + role + " is disabled");
  }

  private boolean needsSecret(String kind) {
    return drivers.stream().anyMatch(driver -> driver.kind().equals(kind) && driver.needsSecret());
  }

  private List<String> kinds() {
    return drivers.stream().map(ChannelDriver::kind).sorted().toList();
  }

  private List<String> pointIds() {
    return roles.points().stream().map(Point::id).toList();
  }

  private boolean isSet(String name) {
    String value = environment.getProperty(name);
    return value != null && !value.isBlank();
  }

  private static ChannelAddress addressOf(String name, String kind, ChannelDef def) {
    List<String> command = def.command();
    return new ChannelAddress(
        name,
        kind,
        def.webhookEnv() == null ? "" : def.webhookEnv(),
        command == null ? List.of() : command);
  }
}

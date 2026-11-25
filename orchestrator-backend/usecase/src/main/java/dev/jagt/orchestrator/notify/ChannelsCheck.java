package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.flow.role.Points;
import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.port.channel.ChannelDriver;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.ConfigFile.NotificationsConfig;
import dev.jagt.orchestrator.service.ConfigFile.NotificationsConfig.ChannelDef;
import dev.jagt.orchestrator.service.ConfigFile.NotificationsConfig.RouteDef;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A route or a channel in {@code jagt.yml} that nothing could tell through is refused, every
 * problem at once; a channel whose variable is unset is dropped and marked, and a route naming the
 * point of a disabled role is inert; both are said once.
 */
@Component
public class ChannelsCheck implements StartupCheck {

  private static final String NOTIFICATIONS = "orchestrator.notifications.";
  private static final String CHANNELS = NOTIFICATIONS + "channels.";
  private static final String POINTS = NOTIFICATIONS + "points.";

  private final ConfigService config;
  private final InstalledRoles roles;
  private final List<ChannelDriver> drivers;
  private final DroppedChannels dropped;

  public ChannelsCheck(
      ConfigService config,
      InstalledRoles roles,
      List<ChannelDriver> drivers,
      DroppedChannels dropped) {
    this.config = config;
    this.roles = roles;
    this.drivers = List.copyOf(drivers);
    this.dropped = dropped;
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
    NotificationsConfig notifications = config.load().notifications();
    List<Problem> notes = new ArrayList<>();
    Map<String, String> silent = dropped.of(notifications);
    silent.forEach((name, variable) ->
        notes.add(droppedNote(notifications.routeDefs(), silent.keySet(), name, variable)));
    Set<String> routed = notifications.routeDefs().keySet();
    if (routed.isEmpty()) {
      notes.add(
          new Problem(
              NOTIFICATIONS + "points",
              NOTIFICATIONS + "points routes no point, so nothing will be told"));
    }
    routed.forEach(id -> disabledRoleOf(id).ifPresent(role -> notes.add(inert(id, role))));
    return notes;
  }

  private static Problem droppedNote(
      Map<String, List<RouteDef>> routes, Set<String> silent, String name, String variable) {
    String key = CHANNELS + name + ".webhookEnv";
    List<String> toldNowhere =
        routes.entrySet().stream()
            .filter(point -> point.getValue().stream().anyMatch(r -> name.equals(r.channel())))
            .filter(point -> point.getValue().stream().allMatch(r -> silent.contains(r.channel())))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    String text =
        key
            + " names the environment variable '"
            + variable
            + "', which is not set, so "
            + name
            + " tells nothing";
    return new Problem(key, toldNowhere.isEmpty() ? text : text + "; told nowhere: " + toldNowhere);
  }

  @Override
  public List<ReachLost> marks() {
    if (!config.fileExists()) {
      return List.of();
    }
    List<ReachLost> marks = new ArrayList<>();
    dropped
        .of(config.load().notifications())
        .forEach((name, variable) ->
            marks.add(
                new ReachLost(
                    ChannelPosts.DRIVER,
                    name,
                    List.of(),
                    name
                        + " tells nothing: the environment variable "
                        + variable
                        + " is not set")));
    return marks;
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

  private static ChannelAddress addressOf(String name, String kind, ChannelDef def) {
    List<String> command = def.command();
    return new ChannelAddress(
        name,
        kind,
        def.webhookEnv() == null ? "" : def.webhookEnv(),
        command == null ? List.of() : command);
  }
}

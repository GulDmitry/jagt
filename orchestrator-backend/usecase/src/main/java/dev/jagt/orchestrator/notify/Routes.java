package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.port.ChannelAddress;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig.ChannelDef;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig.RouteDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Where a point is told: each channel it is routed to, with that route's own title and text. */
@Component
public class Routes {

  /** The templates are filled by {@link Messages}. */
  public record Routed(ChannelAddress channel, String title, String text) {}

  private final ConfigService config;

  public Routes(ConfigService config) {
    this.config = config;
  }

  public List<Routed> of(String point) {
    NotificationsConfig notifications = config.load().notifications();
    List<Routed> routed = new ArrayList<>();
    for (RouteDef route : notifications.routeDefs().getOrDefault(point, List.of())) {
      address(notifications, route.channel())
          .ifPresent(
              channel ->
                  routed.add(new Routed(channel, orEmpty(route.title()), orEmpty(route.text()))));
    }
    return routed;
  }

  private static Optional<ChannelAddress> address(
      NotificationsConfig notifications, @Nullable String name) {
    ChannelDef def = name == null ? null : notifications.channelDefs().get(name);
    String kind = def == null ? null : def.kind();
    if (name == null || def == null || kind == null) {
      return Optional.empty();
    }
    return Optional.of(
        new ChannelAddress(
            name,
            kind,
            orEmpty(def.webhookEnv()),
            def.command() == null ? List.of() : def.command()));
  }

  private static String orEmpty(@Nullable String text) {
    return text == null ? "" : text;
  }
}

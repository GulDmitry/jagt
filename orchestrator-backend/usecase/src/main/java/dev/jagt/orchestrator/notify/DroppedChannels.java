package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.service.ConfigFile.NotificationsConfig;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** A channel whose {@code webhookEnv} names an unset variable tells nothing. */
@Component
public class DroppedChannels {

  private final Environment environment;

  public DroppedChannels(Environment environment) {
    this.environment = environment;
  }

  /** Each dropped channel's name, to the variable it names. */
  public Map<String, String> of(NotificationsConfig notifications) {
    Map<String, String> unset = new TreeMap<>();
    notifications
        .channelDefs()
        .forEach((name, def) -> {
          String variable = def == null ? null : def.webhookEnv();
          if (variable != null && !isSet(variable)) {
            unset.put(name, variable);
          }
        });
    return unset;
  }

  private boolean isSet(String name) {
    String value = environment.getProperty(name);
    return value != null && !value.isBlank();
  }
}

package dev.jagt.orchestrator.adapter;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.Notifier;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.ConfigService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
public class ChannelNotifier implements Notifier {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final JsonMapper MAPPER = new JsonMapper();

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Payload(
      String event,
      @Nullable String task,
      @Nullable String alias,
      @Nullable String title,
      @Nullable String body) {}

  private final ConfigService config;
  private final Processes processes;

  public ChannelNotifier(ConfigService config, Processes processes) {
    this.config = config;
    this.processes = processes;
  }

  @Override
  public String id() {
    return "channel";
  }

  @Override
  public boolean takes(Notification notification) {
    var channel = config.load().notifications().channel();
    return !channel.commandOrEmpty().isEmpty() && channel.eventSet().contains(notification.event());
  }

  @Override
  public void deliver(Notification notification) {
    try {
      List<String> command =
          new ArrayList<>(config.load().notifications().channel().commandOrEmpty());
      command.set(0, Executables.resolve(command.get(0)));
      String json =
          MAPPER.writeValueAsString(
              new Payload(
                  notification.event().id(),
                  notification.taskId(),
                  notification.alias(),
                  notification.title(),
                  notification.body()));
      processes.run(null, TIMEOUT, Map.of(), json, command).expectSuccess("channel command");
    } catch (RuntimeException e) {
      reportFailure(notification, e);
    }
  }

  private void reportFailure(Notification notification, RuntimeException failure) {
    var line =
        log.atWarn()
            .setMessage("channel delivery failed")
            .addKeyValue("channel", id())
            .addKeyValue("event", notification.event().id());
    if (notification.taskId() != null) {
      line = line.addKeyValue("task", notification.taskId());
    }
    line.addKeyValue("cause", Causes.of(failure))
        .addKeyValue("effect", "notification dropped")
        .log();
  }
}

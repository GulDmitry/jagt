package dev.jagt.orchestrator.adapter;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.port.channel.ChannelDriver;
import dev.jagt.orchestrator.port.channel.ChannelMessage;
import dev.jagt.orchestrator.port.host.Processes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CommandChannel implements ChannelDriver {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final JsonMapper MAPPER = new JsonMapper();

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Payload(
      String event,
      @Nullable String task,
      @Nullable String alias,
      @Nullable String title,
      @Nullable String body) {}

  private final Processes processes;

  public CommandChannel(Processes processes) {
    this.processes = processes;
  }

  @Override
  public String kind() {
    return "command";
  }

  @Override
  public void deliver(ChannelAddress address, ChannelMessage message) {
    List<String> command = new ArrayList<>(address.command());
    command.set(0, Executables.resolve(command.get(0)));
    String json =
        MAPPER.writeValueAsString(
            new Payload(
                message.point(),
                message.taskId(),
                message.alias(),
                message.title(),
                message.text()));
    processes.run(null, TIMEOUT, Map.of(), json, command).expectSuccess("channel command");
  }
}

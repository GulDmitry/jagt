package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnExpression(
    "'${orchestrator.opencode.endpoint:}' != '' or '${orchestrator.assistant.local.endpoint:}' !="
        + " ''")
@RequiredArgsConstructor
public class LocalModelReach implements Job {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final OpenCodeProperties opencode;
  private final LocalModelProperties local;
  private final Reach reach;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
  private final JsonMapper mapper = new JsonMapper();

  @Override
  public String id() {
    return "local-model-reach";
  }

  @Override
  public String describe() {
    return "reads whether each local model endpoint answers and serves its model";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  @Override
  public void run() {
    read("opencode", opencode.endpoint(), opencode.model());
    read("assistant.local", local.endpoint(), local.model());
  }

  private void read(String driver, String endpoint, String model) {
    if (endpoint.isEmpty()) {
      return;
    }
    Optional<List<String>> served = served(endpoint);
    if (served.isEmpty()) {
      reach.lost(new ReachLost(driver, endpoint, List.of(), driver + " cannot reach " + endpoint));
    } else if (!served.get().contains(model)) {
      reach.lost(new ReachLost(driver, endpoint, List.of(), endpoint + " does not serve " + model));
    } else {
      reach.reached(driver, endpoint);
    }
  }

  private Optional<List<String>> served(String endpoint) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(endpoint.replaceAll("/+$", "") + "/models"))
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        return Optional.empty();
      }
      return Optional.of(
          mapper
              .readTree(response.body())
              .path("data")
              .valueStream()
              .map(entry -> entry.path("id").asString(""))
              .toList());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }
}

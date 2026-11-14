package dev.jagt.orchestrator.adapter.teams;

import dev.jagt.orchestrator.port.ChannelAddress;
import dev.jagt.orchestrator.port.ChannelDriver;
import dev.jagt.orchestrator.port.ChannelMessage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TeamsWorkflowChannel implements ChannelDriver {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final JsonMapper MAPPER = new JsonMapper();
  private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\"]+");
  private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:)\\]]+$");

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  private final Environment environment;

  public TeamsWorkflowChannel(Environment environment) {
    this.environment = environment;
  }

  @Override
  public String kind() {
    return "teams";
  }

  @Override
  public boolean needsSecret() {
    return true;
  }

  @Override
  public void deliver(ChannelAddress address, ChannelMessage message) {
    String url = environment.getProperty(address.secretEnv());
    if (url == null || url.isBlank()) {
      throw new IllegalStateException(
          "the environment variable " + address.secretEnv() + " is not set");
    }
    URI target = uriOf(url, address.secretEnv());
    HttpRequest request =
        HttpRequest.newBuilder(target)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(envelope(message))))
            .build();
    int status = send(request, target.getHost());
    if (status >= 300) {
      throw new IllegalStateException(
          "the teams webhook at " + target.getHost() + " answered " + status);
    }
  }

  private static URI uriOf(String url, String variable) {
    try {
      return URI.create(url);
    } catch (IllegalArgumentException malformed) {
      throw new IllegalStateException(
          "the environment variable " + variable + " does not hold a valid URL");
    }
  }

  private int send(HttpRequest request, String host) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    } catch (IOException e) {
      throw new IllegalStateException("the teams webhook at " + host + " is unreachable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "the post to the teams webhook at " + host + " was interrupted", e);
    }
  }

  private static Map<String, Object> envelope(ChannelMessage message) {
    Map<String, Object> card = new LinkedHashMap<>();
    card.put("type", "AdaptiveCard");
    card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
    card.put("version", "1.4");
    card.put(
        "body",
        List.of(
            Map.of("type", "TextBlock", "text", message.title(), "weight", "Bolder", "wrap", true),
            Map.of("type", "TextBlock", "text", message.text(), "wrap", true)));
    card.put("actions", actions(message.text()));
    return Map.of(
        "type",
        "message",
        "attachments",
        List.of(Map.of("contentType", "application/vnd.microsoft.card.adaptive", "content", card)));
  }

  private static List<Object> actions(String text) {
    List<Object> actions = new ArrayList<>();
    Matcher links = LINK.matcher(text);
    while (links.find()) {
      String link = TRAILING_PUNCTUATION.matcher(links.group()).replaceFirst("");
      actions.add(Map.of("type", "Action.OpenUrl", "title", link, "url", link));
    }
    return actions;
  }
}

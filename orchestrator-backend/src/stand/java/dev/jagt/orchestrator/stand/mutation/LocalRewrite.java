package dev.jagt.orchestrator.stand.mutation;

import dev.jagt.orchestrator.stand.StandFailure;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public record LocalRewrite(Path directory, String endpoint, String model, TicketRewrite cloud)
    implements TicketRewrite {

  static final String ANSWERED_BY = "rewrite.txt";
  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final JsonMapper MAPPER = new JsonMapper();

  public static TicketRewrite fromEnvironment(Path directory) {
    String endpoint =
        System.getenv().getOrDefault("ORCHESTRATOR_ASSISTANT_LOCAL_ENDPOINT", "").strip();
    TicketRewrite cloud = new HaikuRewrite(directory);
    return endpoint.isEmpty()
        ? cloud
        : new LocalRewrite(
            directory,
            endpoint,
            System.getenv().getOrDefault("ORCHESTRATOR_ASSISTANT_LOCAL_MODEL", ""),
            cloud);
  }

  @Override
  public String rewrite(String ask, String ticket) {
    String cause;
    try {
      HttpResponse<String> response =
          HttpClient.newHttpClient()
              .send(request(ask, ticket), HttpResponse.BodyHandlers.ofString());
      String text =
          response.statusCode() / 100 == 2
              ? MAPPER
                  .readTree(response.body())
                  .path("choices")
                  .path(0)
                  .path("message")
                  .path("content")
                  .asString("")
                  .strip()
              : "";
      if (!text.isEmpty()) {
        answeredBy("local " + model);
        return text;
      }
      cause = "http " + response.statusCode() + " with no text";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      cause = "interrupted";
    } catch (IOException | RuntimeException e) {
      cause = e.getClass().getSimpleName() + " " + e.getMessage();
    }
    answeredBy("cloud, local failed: " + cause);
    return cloud.rewrite(ask, ticket);
  }

  private HttpRequest request(String ask, String ticket) {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("model", model);
    body.put("temperature", 0);
    body.putArray("messages")
        .addObject()
        .put("role", "user")
        .put("content", TicketRewrite.prompt(ask, ticket));
    return HttpRequest.newBuilder(URI.create(endpoint.replaceAll("/+$", "") + "/chat/completions"))
        .timeout(TIMEOUT)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build();
  }

  private void answeredBy(String line) {
    try {
      Files.writeString(directory.resolve(ANSWERED_BY), line + "\n");
    } catch (IOException e) {
      throw new StandFailure("cannot write " + ANSWERED_BY + " under " + directory, e);
    }
  }
}

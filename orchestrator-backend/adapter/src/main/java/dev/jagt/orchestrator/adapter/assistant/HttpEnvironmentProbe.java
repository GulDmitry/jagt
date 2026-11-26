package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.assistant.EnvironmentProbe;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HttpEnvironmentProbe implements EnvironmentProbe {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient http;

  @Override
  public boolean answers(String url) {
    HttpRequest request;
    try {
      request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
    } catch (IllegalArgumentException malformed) {
      log.atError()
          .setMessage("environment address malformed")
          .addKeyValue("url", url)
          .addKeyValue("cause", malformed.toString())
          .addKeyValue("effect", "the environment never answers")
          .addKeyValue("fix", "correct the environment url in jagt.yml")
          .log();
      return false;
    }
    try {
      return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() < 500;
    } catch (IOException unreachable) {
      return false;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}

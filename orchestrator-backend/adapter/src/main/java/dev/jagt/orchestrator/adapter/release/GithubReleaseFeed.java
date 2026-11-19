package dev.jagt.orchestrator.adapter.release;

import dev.jagt.orchestrator.port.host.ReleaseFeed;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reads the releases page the installers read: {@code latest} redirects to the newest tag. */
@Component
@ConditionalOnProperty(
    name = "orchestrator.release-feed.kind",
    havingValue = "github",
    matchIfMissing = true)
class GithubReleaseFeed implements ReleaseFeed {

  private static final Duration CONNECT = Duration.ofSeconds(15);
  private static final Duration LOOKUP = Duration.ofSeconds(30);
  private static final Duration DOWNLOAD = Duration.ofMinutes(10);
  private static final int FOUND = 200;
  private static final int REDIRECT_CLASS = 3;

  private final String base;
  private final HttpClient lookup =
      HttpClient.newBuilder()
          .connectTimeout(CONNECT)
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final HttpClient download =
      HttpClient.newBuilder()
          .connectTimeout(CONNECT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  GithubReleaseFeed(
      @Value("${orchestrator.release-feed.url:https://github.com/GulDmitry/jagt/releases}")
          String url) {
    this.base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  @Override
  public Optional<String> latest() throws IOException {
    HttpResponse<Void> answer =
        send(
            lookup,
            HttpRequest.newBuilder(URI.create(base + "/latest")).timeout(LOOKUP).GET().build(),
            HttpResponse.BodyHandlers.discarding());
    if (answer.statusCode() / 100 != REDIRECT_CLASS) {
      return Optional.empty();
    }
    return answer.headers().firstValue("location").map(GithubReleaseFeed::versionOf);
  }

  @Override
  public void jar(String version, Path target) throws IOException {
    fetch(version, "jagt-" + version + ".jar", target);
  }

  @Override
  public void checksums(String version, Path target) throws IOException {
    fetch(version, "jagt-" + version + ".sha256", target);
  }

  private void fetch(String version, String asset, Path target) throws IOException {
    URI uri = URI.create(base + "/download/v" + version + "/" + asset);
    HttpResponse<Path> answer =
        send(
            download,
            HttpRequest.newBuilder(uri).timeout(DOWNLOAD).GET().build(),
            HttpResponse.BodyHandlers.ofFile(target));
    if (answer.statusCode() != FOUND) {
      throw new IOException("HTTP " + answer.statusCode() + " from " + uri);
    }
  }

  private static <T> HttpResponse<T> send(
      HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> handler)
      throws IOException {
    try {
      return client.send(request, handler);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while reading " + request.uri(), interrupted);
    }
  }

  private static String versionOf(String location) {
    String tag = location.substring(location.lastIndexOf('/') + 1);
    return tag.startsWith("v") ? tag.substring(1) : tag;
  }
}

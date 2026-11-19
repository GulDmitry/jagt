package dev.jagt.orchestrator.adapter.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GithubReleaseFeedTest {

  @Test
  void namesItsDriverAndTheHostOfItsUrl() {
    GithubReleaseFeed feed = new GithubReleaseFeed("https://git.example.invalid/org/app/releases/");

    assertThat(feed.driver()).isEqualTo("github");
    assertThat(feed.host()).isEqualTo("git.example.invalid");
  }

  @Test
  void readsTheLatestVersionFromTheRedirectOfLatest() throws Exception {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/releases/latest",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "/releases/tag/v0.25.0");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.start();
    try {
      GithubReleaseFeed feed =
          new GithubReleaseFeed("http://127.0.0.1:" + server.getAddress().getPort() + "/releases/");

      assertThat(feed.latest()).contains("0.25.0");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void namesNoVersionWhenLatestDoesNotRedirect() throws Exception {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/releases/latest",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    server.start();
    try {
      GithubReleaseFeed feed =
          new GithubReleaseFeed("http://127.0.0.1:" + server.getAddress().getPort() + "/releases");

      assertThat(feed.latest()).isEmpty();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void writesTheReleaseJarAndItsChecksumsWhereAsked(@TempDir Path dir) throws Exception {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/releases/download/v0.25.0/",
        exchange -> {
          byte[] body =
              exchange.getRequestURI().getPath().endsWith(".jar")
                  ? "jar bytes".getBytes(StandardCharsets.UTF_8)
                  : "sums".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      GithubReleaseFeed feed =
          new GithubReleaseFeed("http://127.0.0.1:" + server.getAddress().getPort() + "/releases");

      feed.jar("0.25.0", dir.resolve("a.jar"));
      feed.checksums("0.25.0", dir.resolve("a.sha256"));

      assertThat(dir.resolve("a.jar")).hasContent("jar bytes");
      assertThat(dir.resolve("a.sha256")).hasContent("sums");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void failsWhenTheDownloadAnswersAnHttpError(@TempDir Path dir) throws Exception {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/releases/download/",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    server.start();
    try {
      GithubReleaseFeed feed =
          new GithubReleaseFeed("http://127.0.0.1:" + server.getAddress().getPort() + "/releases");

      assertThatThrownBy(() -> feed.jar("0.25.0", dir.resolve("a.jar")))
          .isInstanceOf(IOException.class);
    } finally {
      server.stop(0);
    }
  }
}

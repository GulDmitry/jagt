package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.jagt.orchestrator.startup.Misconfigured;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class RefusedStartServedUntilSavedTest {

  @Test
  @Timeout(120)
  void refusedStartIsServedOnTheBoardPortAndRethrownWhenTheWaitIsInterrupted(@TempDir Path root)
      throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
    int port;
    try (ServerSocket free = new ServerSocket(0)) {
      port = free.getLocalPort();
    }
    AtomicReference<Throwable> ended = new AtomicReference<>();
    HttpRequest read = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).build();
    Thread launch =
        Thread.ofPlatform()
            .uncaughtExceptionHandler((thread, failure) -> ended.set(failure))
            .start(() ->
                OrchestratorApplication.main(
                    new String[] {
                      "--server.port=" + port,
                      "--orchestrator.terminal=none",
                      "--orchestrator.root=" + root,
                      "--orchestrator.data=" + root,
                      "--spring.config.import=",
                      "--orchestrator.config-file=" + root.resolve("jagt.yml"),
                      "--orchestrator.state-file=" + root.resolve("state.json"),
                      "--logging.file.name=" + root.resolve("jagt.log")
                    }));
    await()
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .until(() ->
            HttpClient.newHttpClient().send(read, HttpResponse.BodyHandlers.ofString()).statusCode()
                == 200);

    launch.interrupt();
    launch.join(Duration.ofSeconds(30));

    assertThat(ended.get()).isInstanceOf(Misconfigured.class);
  }

  @Test
  @Timeout(180)
  void jagtStartsAgainWhenTheOwnerSavesJagtYmlFromTheFailurePage(@TempDir Path root)
      throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
    int port;
    try (ServerSocket free = new ServerSocket(0)) {
      port = free.getLocalPort();
    }
    String base = "http://127.0.0.1:" + port;
    HttpClient client = HttpClient.newHttpClient();
    Thread.ofPlatform()
        .daemon()
        .start(() ->
            OrchestratorApplication.main(
                new String[] {
                  "--server.port=" + port,
                  "--orchestrator.terminal=none",
                  "--orchestrator.root=" + root,
                  "--orchestrator.data=" + root,
                  "--spring.config.import=",
                  "--management.endpoints.web.exposure.include=health,shutdown",
                  "--management.endpoint.shutdown.access=unrestricted",
                  "--orchestrator.config-file=" + root.resolve("jagt.yml"),
                  "--orchestrator.state-file=" + root.resolve("state.json"),
                  "--logging.file.name=" + root.resolve("jagt.log")
                }));
    await()
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .until(() ->
            client
                .send(
                    HttpRequest.newBuilder(URI.create(base + "/")).build(),
                    HttpResponse.BodyHandlers.ofString())
                .body()
                .contains("jagt cannot start"));

    HttpResponse<String> save =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/save"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("yml=orchestrator%3A+%7B%7D%0A"))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(save.statusCode()).isEqualTo(200);
    assertThat(Files.readString(root.resolve("jagt.yml"))).isEqualTo("orchestrator: {}\n");
    await()
        .atMost(Duration.ofSeconds(90))
        .ignoreExceptions()
        .until(() ->
            client
                    .send(
                        HttpRequest.newBuilder(URI.create(base + "/state")).build(),
                        HttpResponse.BodyHandlers.ofString())
                    .statusCode()
                == 200);

    HttpResponse<String> shutdown =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/actuator/shutdown"))
                .header("Origin", base)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    await()
        .atMost(Duration.ofSeconds(60))
        .until(() -> {
          try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500);
            return false;
          } catch (IOException refused) {
            return true;
          }
        });

    assertThat(shutdown.statusCode()).isEqualTo(200);
  }
}

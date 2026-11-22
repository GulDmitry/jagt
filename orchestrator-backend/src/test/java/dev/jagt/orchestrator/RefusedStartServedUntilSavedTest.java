package dev.jagt.orchestrator;

import static dev.jagt.orchestrator.TestLaunchArgsBuilder.launchArgs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.jagt.orchestrator.startup.Misconfigured;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
            .start(() -> OrchestratorApplication.main(launchArgs(root, port).build()));
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
  @Timeout(240)
  void jagtStartsAndServesStateWhenTheOwnerSavesValidConfigOnTheFailurePage(@TempDir Path root)
      throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
    Files.createDirectories(root.resolve("proj/.git"));
    Files.writeString(root.resolve("tmux"), "#!/bin/sh\nexit 0\n");
    root.resolve("tmux").toFile().setExecutable(true);
    int port;
    try (ServerSocket free = new ServerSocket(0)) {
      port = free.getLocalPort();
    }
    String base = "http://127.0.0.1:" + port;
    HttpClient client = HttpClient.newHttpClient();
    AtomicReference<Throwable> ended = new AtomicReference<>();
    Thread.ofPlatform()
        .daemon()
        .uncaughtExceptionHandler((thread, failure) -> ended.set(failure))
        .start(() -> OrchestratorApplication.main(launchArgs(root, port).build()));
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

    String valid =
        "orchestrator:\n  projects:\n    proj:\n      path: "
            + root.resolve("proj")
            + "\n      baseBranch: origin/main\n";
    client.send(
        HttpRequest.newBuilder(URI.create(base + "/save"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "yml=" + URLEncoder.encode(valid, StandardCharsets.UTF_8)))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    await()
        .atMost(Duration.ofMinutes(2))
        .ignoreExceptions()
        .until(() ->
            client
                    .send(
                        HttpRequest.newBuilder(URI.create(base + "/state")).build(),
                        HttpResponse.BodyHandlers.ofString())
                    .body()
                    .startsWith("{")
                || ended.get() != null);

    assertThat(ended.get()).isNull();
    assertThat(Files.readString(root.resolve("jagt.yml"))).isEqualTo(valid);
  }
}

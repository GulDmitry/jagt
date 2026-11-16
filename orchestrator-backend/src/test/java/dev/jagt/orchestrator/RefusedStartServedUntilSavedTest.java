package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.jagt.orchestrator.startup.Misconfigured;
import java.net.ServerSocket;
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
}

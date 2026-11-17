package dev.jagt.orchestrator.stand.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.jagt.orchestrator.stand.StandFailure;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRewriteFallbackTest {

  @TempDir Path directory;

  private HttpServer server;
  private int status;
  private String answer;

  @BeforeEach
  void startEndpoint() throws IOException {
    status = 200;
    answer = "{\"choices\":[{\"message\":{\"content\":\" rewritten \"}}]}";
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          try (exchange) {
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
          }
        });
    server.start();
  }

  @AfterEach
  void stopEndpoint() {
    server.stop(0);
  }

  @Test
  void theLocalModelsTextIsReturnedStrippedAndRecordedAsTheAnswerer() throws Exception {
    String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
    LocalRewrite rewrite = new LocalRewrite(directory, endpoint, "tiny", (ask, ticket) -> "cloud");

    String text = rewrite.rewrite("ask", "ticket");

    assertThat(text).isEqualTo("rewritten");
    assertThat(Files.readString(directory.resolve("rewrite.txt"))).isEqualTo("local tiny\n");
  }

  @Test
  void theCloudAnswersWhenTheLocalEndpointRefuses() throws Exception {
    status = 500;
    String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
    LocalRewrite rewrite = new LocalRewrite(directory, endpoint, "tiny", (ask, ticket) -> "cloud");

    String text = rewrite.rewrite("ask", "ticket");

    assertThat(text).isEqualTo("cloud");
    assertThat(Files.readString(directory.resolve("rewrite.txt")))
        .isEqualTo("cloud, local failed: http 500 with no text\n");
  }

  @Test
  void theCloudAnswersWhenTheLocalEndpointIsDown() throws Exception {
    String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
    server.stop(0);
    LocalRewrite rewrite = new LocalRewrite(directory, endpoint, "tiny", (ask, ticket) -> "cloud");

    String text = rewrite.rewrite("ask", "ticket");

    assertThat(text).isEqualTo("cloud");
    assertThat(Files.readString(directory.resolve("rewrite.txt")))
        .startsWith("cloud, local failed");
  }

  @Test
  void theCloudAnswersWhenTheLocalAnswerIsNotJson() {
    answer = "<html>";
    String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
    LocalRewrite rewrite = new LocalRewrite(directory, endpoint, "tiny", (ask, ticket) -> "cloud");

    String text = rewrite.rewrite("ask", "ticket");

    assertThat(text).isEqualTo("cloud");
  }

  @Test
  void rewriteWhoseAnswerCannotBeRecordedIsFailure() {
    String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
    LocalRewrite rewrite =
        new LocalRewrite(directory.resolve("missing"), endpoint, "tiny", (ask, ticket) -> "cloud");

    assertThatThrownBy(() -> rewrite.rewrite("ask", "ticket")).isInstanceOf(StandFailure.class);
  }
}

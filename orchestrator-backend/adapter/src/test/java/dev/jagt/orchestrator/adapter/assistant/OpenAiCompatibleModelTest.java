package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleModelTest {

  private static final String SCHEMA = "{\"type\":\"object\",\"required\":[\"command\"]}";
  private final UsageTracker usageTracker = mock(UsageTracker.class);
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void answersWithTheObjectTheServerReturnsAndBooksItsTokens() throws IOException {
    AtomicReference<String> sent = new AtomicReference<>();
    start(
        200,
        "{\"choices\":[{\"message\":{\"content\":\"{\\\"command\\\":\\\"ship\\\"}\"}}],"
            + "\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":9}}",
        Duration.ZERO,
        sent);

    var answer =
        model(Duration.ofSeconds(5))
            .ask("map this", SCHEMA, "command mapping", AssistantCallKind.COMMAND_MAP);

    assertThat(answer.facts().orElseThrow().path("command").asString("")).isEqualTo("ship");
    verify(usageTracker).record(AssistantCallKind.COMMAND_MAP, TokenUsage.ofCall(120, 0, 9, 0));
  }

  @Test
  void sendsTheSchemaAsTheResponseFormatAndTheConfiguredModel() throws IOException {
    AtomicReference<String> sent = new AtomicReference<>();
    start(200, "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}", Duration.ZERO, sent);

    model(Duration.ofSeconds(5))
        .ask("map this", SCHEMA, "command mapping", AssistantCallKind.COMMAND_MAP);

    assertThat(sent.get())
        .contains("\"model\":\"qwen\"")
        .contains("\"type\":\"json_schema\"")
        .contains("\"required\":[\"command\"]")
        .contains("map this");
  }

  @Test
  void failsWithoutAnswerWhereTheServerAnswersAnError() throws IOException {
    start(500, "boom", Duration.ZERO, new AtomicReference<>());

    var answer =
        model(Duration.ofSeconds(5))
            .ask("map this", SCHEMA, "command mapping", AssistantCallKind.COMMAND_MAP);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).isEqualTo("http 500");
  }

  @Test
  void failsWithoutAnswerWhereTheContentIsNotAnObject() throws IOException {
    start(
        200,
        "{\"choices\":[{\"message\":{\"content\":\"[1]\"}}]}",
        Duration.ZERO,
        new AtomicReference<>());

    var answer =
        model(Duration.ofSeconds(5))
            .ask("map this", SCHEMA, "command mapping", AssistantCallKind.COMMAND_MAP);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).isEqualTo("content is not an object");
  }

  @Test
  void failsWithinTheTimeoutWhereTheServerOutlastsIt() throws IOException {
    start(200, "{}", Duration.ofSeconds(4), new AtomicReference<>());
    long begun = System.nanoTime();

    var answer =
        model(Duration.ofSeconds(1))
            .ask("map this", SCHEMA, "command mapping", AssistantCallKind.COMMAND_MAP);

    assertThat(answer.facts()).isEmpty();
    assertThat(Duration.ofNanos(System.nanoTime() - begun)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void failsWithoutAnswerWhereNothingListens() throws IOException {
    start(200, "{}", Duration.ZERO, new AtomicReference<>());
    int port = server.getAddress().getPort();
    server.stop(0);
    server = null;
    OpenAiCompatibleModel dead =
        new OpenAiCompatibleModel(
            LocalModelProperties.off()
                .withEndpoint("http://127.0.0.1:" + port + "/v1")
                .withModel("qwen"),
            usageTracker);

    var answer = dead.ask("map this", SCHEMA, "command mapping", AssistantCallKind.COMMAND_MAP);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.usage()).isEqualTo(TokenUsage.NONE);
  }

  private OpenAiCompatibleModel model(Duration timeout) {
    return new OpenAiCompatibleModel(
        LocalModelProperties.off()
            .withEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
            .withModel("qwen")
            .withTimeout(timeout),
        usageTracker);
  }

  private void start(int status, String body, Duration delay, AtomicReference<String> sent)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          sent.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          try {
            Thread.sleep(delay.toMillis());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (InterruptedException | IOException ignored) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
  }
}

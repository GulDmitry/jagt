package dev.jagt.orchestrator.adapter.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.jagt.orchestrator.port.ChannelAddress;
import dev.jagt.orchestrator.port.ChannelMessage;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TeamsWorkflowChannelTest {

  private static final ChannelAddress TEAMS =
      new ChannelAddress("chat", "teams", "ABC_HOOK", List.of());

  private static HttpServer answering(int status, AtomicReference<String> body) throws Exception {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/hook",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static StandardEnvironment hookAt(HttpServer server) {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of("ABC_HOOK", "http://127.0.0.1:" + server.getAddress().getPort() + "/hook")));
    return environment;
  }

  @Test
  void postsAnAdaptiveCardWithTheLinksAsActions() throws Exception {
    AtomicReference<String> body = new AtomicReference<>();
    HttpServer server = answering(202, body);
    try {
      new TeamsWorkflowChannel(hookAt(server))
          .deliver(
              TEAMS,
              new ChannelMessage(
                  "deployed",
                  "ABC-42",
                  null,
                  "ABC-42 deployed",
                  "live: http://localhost:8290/?task=ABC-42."));
    } finally {
      server.stop(0);
    }

    JsonNode card = new JsonMapper().readTree(body.get()).at("/attachments/0/content");
    assertThat(card.get("type").asString()).isEqualTo("AdaptiveCard");
    assertThat(card.at("/body/0/text").asString()).isEqualTo("ABC-42 deployed");
    assertThat(card.at("/actions/0/type").asString()).isEqualTo("Action.OpenUrl");
    assertThat(card.at("/actions/0/url").asString())
        .isEqualTo("http://localhost:8290/?task=ABC-42");
  }

  @Test
  void namesTheHostWhenTheWebhookAnswers4xx() throws Exception {
    HttpServer server = answering(400, new AtomicReference<>());
    TeamsWorkflowChannel channel = new TeamsWorkflowChannel(hookAt(server));
    ChannelMessage message = new ChannelMessage("stopped", "ABC-42", null, "stopped", "silent");
    try {
      assertThatThrownBy(() -> channel.deliver(TEAMS, message))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("127.0.0.1")
          .hasMessageContaining("400");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void keepsTheUrlOutOfTheFailureWhenTheEnvironmentHoldsMalformedOne() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource("test", Map.of("ABC_HOOK", "https://hook.invalid/TOKEN 42")));
    ChannelMessage message = new ChannelMessage("stopped", "ABC-42", null, "stopped", "silent");

    assertThatThrownBy(() -> new TeamsWorkflowChannel(environment).deliver(TEAMS, message))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ABC_HOOK")
        .hasMessageNotContaining("TOKEN");
  }

  @Test
  void isTheDriverOfKindTeams() {
    assertThat(new TeamsWorkflowChannel(new StandardEnvironment()).kind()).isEqualTo("teams");
  }
}

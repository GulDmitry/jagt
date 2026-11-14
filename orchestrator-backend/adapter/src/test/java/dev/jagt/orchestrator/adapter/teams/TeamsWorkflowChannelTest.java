package dev.jagt.orchestrator.adapter.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.port.channel.ChannelMessage;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TeamsWorkflowChannelTest {

  private static final ChannelAddress TEAMS =
      new ChannelAddress("chat", "teams", "ABC_HOOK", List.of());

  private final AtomicReference<String> body = new AtomicReference<>();
  private final int[] status = {202};
  private final StandardEnvironment environment = new StandardEnvironment();
  private HttpServer server;

  @BeforeEach
  void listen() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/hook",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(status[0], -1);
          exchange.close();
        });
    server.start();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of("ABC_HOOK", "http://127.0.0.1:" + server.getAddress().getPort() + "/hook")));
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void postsAnAdaptiveCardWithTheLinksAsActions() throws Exception {
    new TeamsWorkflowChannel(environment)
        .deliver(
            TEAMS,
            new ChannelMessage(
                "deployed",
                "ABC-42",
                null,
                "ABC-42 deployed",
                "live: http://localhost:8290/?task=ABC-42."));

    JsonNode card = new JsonMapper().readTree(body.get()).at("/attachments/0/content");
    assertThat(card.get("type").asString()).isEqualTo("AdaptiveCard");
    assertThat(card.at("/body/0/text").asString()).isEqualTo("ABC-42 deployed");
    assertThat(card.at("/actions/0/type").asString()).isEqualTo("Action.OpenUrl");
    assertThat(card.at("/actions/0/url").asString())
        .isEqualTo("http://localhost:8290/?task=ABC-42");
  }

  @Test
  void namesTheHostWhenTheWebhookAnswers4xx() {
    status[0] = 400;
    TeamsWorkflowChannel channel = new TeamsWorkflowChannel(environment);
    ChannelMessage message = new ChannelMessage("stopped", "ABC-42", null, "stopped", "silent");

    assertThatThrownBy(() -> channel.deliver(TEAMS, message))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("127.0.0.1")
        .hasMessageContaining("400");
  }

  @Test
  void keepsTheUrlOutOfTheFailureWhenTheEnvironmentHoldsMalformedOne() {
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

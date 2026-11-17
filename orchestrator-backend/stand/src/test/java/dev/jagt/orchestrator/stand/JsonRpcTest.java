package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JsonRpcTest {

  @TempDir Path directory;

  private JsonRpc rpc;

  @BeforeEach
  void startRpc() {
    rpc = new JsonRpc(new StandTools(new StandStore(directory, "http://127.0.0.1:1")));
  }

  @Test
  void bodyThatIsNoJsonAnswersParseError() {
    JsonNode answer = JsonMapper.builder().build().readTree(rpc.handle("{nope").orElseThrow());

    assertThat(answer.path("error").path("code").asInt()).isEqualTo(-32700);
  }

  @Test
  void notificationWithoutAnIdAnswersNothing() {
    assertThat(rpc.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
        .isEmpty();
  }

  @Test
  void initializeEchoesTheClientsProtocolVersion() {
    JsonNode answer =
        JsonMapper.builder()
            .build()
            .readTree(
                rpc.handle(
                        "{\"id\":1,\"method\":\"initialize\","
                            + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}")
                    .orElseThrow());

    assertThat(answer.path("result").path("protocolVersion").asString()).isEqualTo("2024-11-05");
  }

  @Test
  void initializeWithoutVersionAnswersTheDefaultOne() {
    JsonNode answer =
        JsonMapper.builder()
            .build()
            .readTree(rpc.handle("{\"id\":1,\"method\":\"initialize\"}").orElseThrow());

    assertThat(answer.path("result").path("protocolVersion").asString()).isEqualTo("2025-06-18");
  }

  @Test
  void pingAnswersAnEmptyResultForTheSameId() {
    JsonNode answer =
        JsonMapper.builder()
            .build()
            .readTree(rpc.handle("{\"id\":7,\"method\":\"ping\"}").orElseThrow());

    assertThat(answer.path("id").asInt()).isEqualTo(7);
    assertThat(answer.path("result")).isEmpty();
  }

  @Test
  void toolsListAnswersTheDefinitions() {
    JsonNode answer =
        JsonMapper.builder()
            .build()
            .readTree(rpc.handle("{\"id\":1,\"method\":\"tools/list\"}").orElseThrow());

    assertThat(answer.path("result").path("tools")).isNotEmpty();
  }

  @Test
  void toolCallAnswersItsResultAsText() {
    JsonNode answer =
        JsonMapper.builder()
            .build()
            .readTree(
                rpc.handle(
                        "{\"id\":1,\"method\":\"tools/call\","
                            + "\"params\":{\"name\":\"get_current_user\",\"arguments\":{}}}")
                    .orElseThrow());

    assertThat(answer.path("result").path("isError").asBoolean()).isFalse();
    assertThat(answer.path("result").path("content").get(0).path("text").asString())
        .contains("agent");
  }

  @Test
  void toolCallThatFailsAnswersTheCauseAsAnError() {
    JsonNode answer =
        JsonMapper.builder()
            .build()
            .readTree(
                rpc.handle(
                        "{\"id\":1,\"method\":\"tools/call\","
                            + "\"params\":{\"name\":\"nope\",\"arguments\":{}}}")
                    .orElseThrow());

    assertThat(answer.path("result").path("isError").asBoolean()).isTrue();
    assertThat(answer.path("result").path("content").get(0).path("text").asString())
        .startsWith("cause: unknown tool nope");
  }

  @Test
  void anUnknownMethodAnswersMethodNotFound() {
    JsonNode answer =
        JsonMapper.builder()
            .build()
            .readTree(rpc.handle("{\"id\":1,\"method\":\"what\"}").orElseThrow());

    assertThat(answer.path("error").path("code").asInt()).isEqualTo(-32601);
  }
}

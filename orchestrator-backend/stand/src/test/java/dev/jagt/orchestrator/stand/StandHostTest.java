package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StandHostTest {

  @TempDir Path directory;

  private final HttpClient client = HttpClient.newHttpClient();
  private StandHost host;
  private HttpRequest.Builder mcp;

  @BeforeEach
  void startHost() throws Exception {
    host = StandHost.listen(directory, InetAddress.getLoopbackAddress()).start();
    mcp =
        HttpRequest.newBuilder(URI.create(host.mcpUrl("127.0.0.1")))
            .header("Content-Type", "application/json");
  }

  @AfterEach
  void stopHost() {
    host.close();
  }

  @Test
  void answersInitializeWithItsNameAndTheRequestedProtocolVersion() throws Exception {
    HttpResponse<String> response =
        client.send(
            mcp.POST(
                    BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                            + "{\"protocolVersion\":\"2024-11-05\"}}"))
                .build(),
            BodyHandlers.ofString());

    JsonNode result = JsonMapper.builder().build().readTree(response.body()).path("result");
    assertThat(result.path("serverInfo").path("name").asString()).isEqualTo("stand-host");
    assertThat(result.path("protocolVersion").asString()).isEqualTo("2024-11-05");
    assertThat(result.path("capabilities").has("tools")).isTrue();
  }

  @Test
  void answersTheFakeGhWithItsOutputAndRefusesAnUnknownCommandWith400() throws Exception {
    HttpRequest known =
        HttpRequest.newBuilder(URI.create(host.baseUrl() + "/gh"))
            .POST(BodyPublishers.ofString("auth\0status\0"))
            .build();
    HttpRequest unknown =
        HttpRequest.newBuilder(URI.create(host.baseUrl() + "/gh"))
            .POST(BodyPublishers.ofString("gist\0create\0"))
            .build();

    HttpResponse<String> answered = client.send(known, BodyHandlers.ofString());
    HttpResponse<String> refused = client.send(unknown, BodyHandlers.ofString());

    assertThat(answered.statusCode()).isEqualTo(200);
    assertThat(answered.body()).contains("Logged in");
    assertThat(refused.statusCode()).isEqualTo(400);
  }

  @Test
  void acceptsTheInitializedNotificationWithoutBody() throws Exception {
    HttpResponse<String> response =
        client.send(
            mcp.POST(
                    BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(202);
    assertThat(response.body()).isEmpty();
  }

  @Test
  void answersAnUnknownMethodWithMethodNotFound() throws Exception {
    HttpResponse<String> response =
        client.send(
            mcp.POST(BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"nope\"}"))
                .build(),
            BodyHandlers.ofString());

    JsonNode error = JsonMapper.builder().build().readTree(response.body()).path("error");
    assertThat(error.path("code").asInt()).isEqualTo(-32601);
  }

  @Test
  void keepsOneRequestWhenTheSameBranchIsCreatedTwice() throws Exception {
    HttpRequest create =
        mcp.POST(
                BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":"
                        + "{\"name\":\"create_merge_request\",\"arguments\":"
                        + "{\"source_branch\":\"feature/abc-42\",\"target_branch\":\"main\","
                        + "\"title\":\"ABC-42\",\"description\":\"d\"}}}"))
            .build();
    client.send(create, BodyHandlers.ofString());
    client.send(create, BodyHandlers.ofString());

    String list =
        client
            .send(
                mcp.POST(
                        BodyPublishers.ofString(
                            "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":"
                                + "{\"name\":\"list_merge_requests\",\"arguments\":"
                                + "{\"source_branch\":\"feature/abc-42\"}}}"))
                    .build(),
                BodyHandlers.ofString())
            .body();
    String text =
        JsonMapper.builder()
            .build()
            .readTree(list)
            .path("result")
            .path("content")
            .get(0)
            .path("text")
            .asString();
    assertThat(JsonMapper.builder().build().readTree(text).size()).isEqualTo(1);
  }

  @Test
  void resolvesThreadTheScenarioAddedAndReadsItBack() throws Exception {
    host.store().createRequest("feature/abc-42", "main", "ABC-42", "d");
    host.store().addThread(1, "src/A.java", 7, "rename this");

    HttpResponse<String> before =
        client.send(
            mcp.POST(
                    BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":"
                            + "{\"name\":\"get_merge_request\",\"arguments\":{\"id_or_url\":\""
                            + host.baseUrl()
                            + "/requests/1\"}}}"))
                .build(),
            BodyHandlers.ofString());
    client.send(
        mcp.POST(
                BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":"
                        + "{\"name\":\"resolve_thread\","
                        + "\"arguments\":{\"id\":1,\"thread_id\":1}}}"))
            .build(),
        BodyHandlers.ofString());

    assertThat(before.body()).contains("rename this").contains("reviewer");
    assertThat(host.store().request(1).path("threads").get(0).path("resolved").asBoolean())
        .isTrue();
  }

  @Test
  void listsThePipelineOfRequestWithItsStatusAndBranch() throws Exception {
    host.store().createRequest("feature/abc-42", "main", "ABC-42", "d");
    host.store().setPipeline(1, "success");

    String answer =
        client
            .send(
                mcp.POST(
                        BodyPublishers.ofString(
                            "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":"
                                + "{\"name\":\"list_merge_request_pipelines\","
                                + "\"arguments\":{\"id\":1}}}"))
                    .build(),
                BodyHandlers.ofString())
            .body();

    String text =
        JsonMapper.builder()
            .build()
            .readTree(answer)
            .path("result")
            .path("content")
            .get(0)
            .path("text")
            .asString();
    JsonNode pipelines = JsonMapper.builder().build().readTree(text);
    assertThat(pipelines.size()).isEqualTo(1);
    assertThat(pipelines.get(0).path("status").asString()).isEqualTo("success");
    assertThat(pipelines.get(0).path("ref").asString()).isEqualTo("feature/abc-42");
    assertThat(pipelines.get(0).path("id").asInt()).isEqualTo(1);
  }

  @Test
  void failsAnUnknownToolAsToolError() throws Exception {
    HttpResponse<String> response =
        client.send(
            mcp.POST(
                    BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\","
                            + "\"params\":{\"name\":\"merge_everything\",\"arguments\":{}}}"))
                .build(),
            BodyHandlers.ofString());

    JsonNode result = JsonMapper.builder().build().readTree(response.body()).path("result");
    assertThat(result.path("isError").asBoolean()).isTrue();
    assertThat(result.path("content").get(0).path("text").asString())
        .contains("unknown tool merge_everything");
  }

  @Test
  void hostBoundToAllInterfacesAnswersOnTheMachinesOwnAddress() throws Exception {
    InetAddress own;
    try (DatagramSocket route = new DatagramSocket()) {
      route.connect(InetAddress.getByName("192.0.2.1"), 9);
      own = route.getLocalAddress();
    }
    boolean loopbackOnly = own.isLoopbackAddress() || own.isAnyLocalAddress();
    Assumptions.assumeFalse(loopbackOnly, "loopback only"); // needs a real address

    try (StandHost open = StandHost.listen(directory, InetAddress.getByName("0.0.0.0")).start()) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create(open.appUrl(own.getHostAddress()))).build(),
              BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
    }
  }

  @Test
  void servesTheDeployedGreetingAtItsAppUrl() throws Exception {
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(host.appUrl("127.0.0.1"))).build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("Hello, World");
  }
}

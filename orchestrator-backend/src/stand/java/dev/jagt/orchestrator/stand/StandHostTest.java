package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StandHostTest {

    @TempDir
    Path directory;

    @Test
    void answersInitializeWithItsNameAndTheRequestedProtocolVersion() throws Exception {
        try (StandHost host = new StandHost(directory).start()) {
            HttpResponse<String> response = post(host,
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\"}}");

            JsonNode result = JsonMapper.builder().build().readTree(response.body()).path("result");
            assertThat(result.path("serverInfo").path("name").asString()).isEqualTo("stand-host");
            assertThat(result.path("protocolVersion").asString()).isEqualTo("2024-11-05");
            assertThat(result.path("capabilities").has("tools")).isTrue();
        }
    }

    @Test
    void acceptsTheInitializedNotificationWithoutABody() throws Exception {
        try (StandHost host = new StandHost(directory).start()) {
            HttpResponse<String> response = post(host, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

            assertThat(response.statusCode()).isEqualTo(202);
            assertThat(response.body()).isEmpty();
        }
    }

    @Test
    void answersAnUnknownMethodWithMethodNotFound() throws Exception {
        try (StandHost host = new StandHost(directory).start()) {
            HttpResponse<String> response = post(host, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"nope\"}");

            JsonNode error = JsonMapper.builder().build().readTree(response.body()).path("error");
            assertThat(error.path("code").asInt()).isEqualTo(-32601);
        }
    }

    @Test
    void keepsOneRequestWhenTheSameBranchIsCreatedTwice() throws Exception {
        try (StandHost host = new StandHost(directory).start()) {
            String create = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"create_merge_request\","
                    + "\"arguments\":{\"source_branch\":\"feature/abc-42\",\"target_branch\":\"main\",\"title\":\"ABC-42\","
                    + "\"description\":\"d\"}}}";
            post(host, create);
            post(host, create);

            String list = post(host, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":"
                    + "\"list_merge_requests\",\"arguments\":{\"source_branch\":\"feature/abc-42\"}}}").body();
            String text = JsonMapper.builder().build().readTree(list).path("result").path("content").get(0).path("text").asString();
            assertThat(JsonMapper.builder().build().readTree(text).size()).isEqualTo(1);
        }
    }

    @Test
    void resolvesAThreadTheScenarioAddedAndReadsItBack() throws Exception {
        try (StandHost host = new StandHost(directory).start()) {
            host.store().createRequest("feature/abc-42", "main", "ABC-42", "d");
            host.store().addThread(1, "src/A.java", 7, "rename this");

            HttpResponse<String> before = post(host, "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"get_merge_request\",\"arguments\":{\"id_or_url\":\"" + host.baseUrl() + "/requests/1\"}}}");
            post(host, "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"resolve_thread\","
                    + "\"arguments\":{\"id\":1,\"thread_id\":1}}}");

            assertThat(before.body()).contains("rename this").contains("reviewer");
            assertThat(host.store().request(1).path("threads").get(0).path("resolved").asBoolean()).isTrue();
        }
    }

    @Test
    void failsAnUnknownToolAsAToolError() throws Exception {
        try (StandHost host = new StandHost(directory).start()) {
            HttpResponse<String> response = post(host, "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"merge_everything\",\"arguments\":{}}}");

            JsonNode result = JsonMapper.builder().build().readTree(response.body()).path("result");
            assertThat(result.path("isError").asBoolean()).isTrue();
            assertThat(result.path("content").get(0).path("text").asString()).contains("unknown tool merge_everything");
        }
    }

    private static HttpResponse<String> post(StandHost host, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(host.mcpUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}

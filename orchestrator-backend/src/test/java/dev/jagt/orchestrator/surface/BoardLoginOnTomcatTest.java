package dev.jagt.orchestrator.surface;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.port.terminal.EditorDriver;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.import=",
      "orchestrator.open-terminal-window=false",
      "orchestrator.startup-checks=false",
      "orchestrator.usage-limits.enabled=false",
      "orchestrator.auth.mode=password",
      "orchestrator.auth.password-hash="
          + "$2y$04$cpM1ODse0DnRCsm1Zpa9quQKnFm0ZxoiaduZfAt4nnLFQp0rBW332"
    })
class BoardLoginOnTomcatTest {

  @MockitoBean private UserNotifier userNotifier;

  @TempDir static Path root;

  @MockitoBean private EditorDriver editorDriver;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void keepConfigAndStateInTheTempRoot(DynamicPropertyRegistry registry) throws IOException {
    ShippedRoot.configure(registry, root);
  }

  @Test
  void sendsBrowserToTheLoginPageAndAnswersTheApi401WhenNobodyIsLoggedIn() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    URI base = URI.create("http://127.0.0.1:" + port);
    HttpRequest board = HttpRequest.newBuilder(base).header("Accept", "text/html").build();
    HttpRequest api = HttpRequest.newBuilder(base.resolve("/api/tasks")).build();

    HttpResponse<String> page = client.send(board, HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> refused = client.send(api, HttpResponse.BodyHandlers.ofString());

    assertThat(page.statusCode()).isEqualTo(302);
    assertThat(page.headers().firstValue("Location").orElse("")).endsWith("/login.html");
    assertThat(refused.statusCode()).isEqualTo(401);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/login.html",
        "/app.css",
        "/core/i18n.js",
        "/i18n/index.json",
        "/i18n/en.json",
        "/user/i18n/index.json"
      })
  void servesTheLoginPageAndEveryAssetItFetchesWhenNobodyIsLoggedIn(String path) throws Exception {
    Files.createDirectories(root.resolve("i18n"));
    Files.writeString(root.resolve("i18n/index.json"), "[]");
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void refusesTheDebugApiReadWhenNobodyIsLoggedIn() throws Exception {
    HttpRequest read =
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/debug/records/backend-log"))
            .build();

    HttpResponse<String> refused =
        HttpClient.newHttpClient().send(read, HttpResponse.BodyHandlers.ofString());

    assertThat(refused.statusCode()).isEqualTo(401);
  }

  @Test
  void refusesTheDebugMcpReadWithNoTokenAndNoWorktree() throws Exception {
    HttpRequest read =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                        + "{\"name\":\"read_record\",\"arguments\":{\"record\":\"stops\"}}}"))
            .build();

    HttpResponse<String> refused =
        HttpClient.newHttpClient().send(read, HttpResponse.BodyHandlers.ofString());

    assertThat(refused.statusCode()).isEqualTo(401);
  }

  @Test
  void servesSessionOnMcpByItsWorktreeHeaderWithNoLogin() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("X-Working-Directory", "/work/abc-42")
            .POST(HttpRequest.BodyPublishers.ofString("{"))
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @ParameterizedTest
  @CsvSource({"open-sesame, /", "guess, /login.html?error"})
  void letsTheOwnerInWithThePasswordAndRefusesAnyOther(String password, String landing)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
            .header("Origin", "http://127.0.0.1:" + port)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=" + password))
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location").orElse("")).endsWith(landing);
  }

  @Test
  void keepsTheOwnersSessionOpenToTheApi() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest login =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
            .header("Origin", "http://127.0.0.1:" + port)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=open-sesame"))
            .build();
    String cookie =
        client
            .send(login, HttpResponse.BodyHandlers.ofString())
            .headers()
            .firstValue("Set-Cookie")
            .orElse("");
    HttpRequest api =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/tasks"))
            .header("Cookie", cookie.substring(0, cookie.indexOf(';')))
            .build();

    HttpResponse<String> response = client.send(api, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
  }
}

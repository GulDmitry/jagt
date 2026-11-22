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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.import=",
      "orchestrator.terminal=none",
      "orchestrator.startup-checks=false",
      "orchestrator.usage-limits.enabled=false",
      "orchestrator.auth.mode=password",
      "orchestrator.auth.password-hash="
          + "$2y$04$cpM1ODse0DnRCsm1Zpa9quQKnFm0ZxoiaduZfAt4nnLFQp0rBW332"
    })
class BoardOnTomcatTest {

  @MockitoBean private UserNotifier userNotifier;

  @MockitoBean private EditorDriver editorDriver;

  @TempDir static Path root;

  @LocalServerPort private int port;

  private final ApplicationContext context;

  @Autowired
  BoardOnTomcatTest(ApplicationContext context) {
    this.context = context;
  }

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
        "/login.js",
        "/app.css",
        "/ui/translate.js",
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

  @ParameterizedTest
  @CsvSource({
    "/api/tasks, ''",
    "/actuator/metrics, jvm.memory.used",
    "/actuator/metrics/http.server.requests, http.server.requests",
    "/actuator/info, version"
  })
  void keepsTheOwnersSessionOpenToTheApiTheMetricsAndTheBuildVersion(String path, String expected)
      throws Exception {
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
    HttpRequest read =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Cookie", cookie.substring(0, cookie.indexOf(';')))
            .build();

    HttpResponse<String> response = client.send(read, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "/api;x/tasks/actions/deploy?task=ABC-42, 400",
    "/%61pi/tasks/actions/deploy?task=ABC-42, 403"
  })
  void refusesBoardWriteWithoutAnOriginHoweverItsPathIsSpelled(String path, int status)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(status);
  }

  @Test
  void readinessProbeWaitsForRunningClonesAndTheFinishedStart() {
    String readiness =
        context.getEnvironment().getProperty("management.endpoint.health.group.readiness.include");

    assertThat(readiness).isNotNull();
    assertThat(readiness.split(",")).contains("clones", "readinessState");
  }

  @Test
  void isNotReadyWhileTheStartIsNotFinished() throws Exception {
    AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness"))
            .build();

    int status =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();

    assertThat(status).isEqualTo(503);
  }

  @Test
  void isAliveWithoutLoginWhileNotReady() throws Exception {
    AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health/liveness"))
            .build();

    int status =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();

    assertThat(status).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/actuator/metrics", "/actuator/info"})
  void refusesMetricsAndInfoWhenNobodyIsLoggedIn(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Accept", "application/json")
            .build();

    int status =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();

    assertThat(status).isIn(302, 401);
  }
}

package dev.jagt.orchestrator.surface.board.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.service.session.SessionStreams;
import dev.jagt.orchestrator.surface.board.auth.attempts.LoginAttempts;
import dev.jagt.orchestrator.surface.board.auth.attempts.LoginAudit;
import dev.jagt.orchestrator.surface.board.auth.attempts.LoginBackoff;
import dev.jagt.orchestrator.surface.board.auth.attempts.PasswordLoginOutcome;
import dev.jagt.orchestrator.surface.board.files.BoardFolder;
import dev.jagt.orchestrator.surface.board.files.BoardProperties;
import dev.jagt.orchestrator.surface.board.filter.Doors;
import dev.jagt.orchestrator.surface.board.terminal.SessionSocket;
import dev.jagt.orchestrator.surface.board.terminal.SessionSocketRoute;
import dev.jagt.orchestrator.surface.mcp.refusal.RootToken;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeHost;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = PasswordLoginRefusalOnTomcatTest.Board.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "orchestrator.auth.mode=password",
      "orchestrator.auth.backoff.failures=2",
      "server.forward-headers-strategy=native",
      "orchestrator.auth.passwordHash=$2y$04$.W6F.lVT3xhLTvtg8hW1WuEf2QP.enJ4zNrFWqlXmuBhzZndQnIiu"
    })
@MockitoBean(types = {UserNotifier.class, SessionStreams.class})
class PasswordLoginRefusalOnTomcatTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableConfigurationProperties({AuthProperties.class, LoginBackoff.class})
  @Import({
    PasswordLogin.class,
    Doors.class,
    LoginAttempts.class,
    LoginAudit.class,
    PasswordLoginOutcome.class,
    SessionSocket.class,
    SessionSocketRoute.class
  })
  static class Board {
    @Bean
    BoardCsp boardCsp() {
      return new BoardCsp(
          new BoardFolder(
              new BoardProperties(null),
              new OrchestratorPaths(OrchestratorProperties.defaults(), FakeHost.machine())));
    }

    @Bean
    RootToken rootToken() {
      return header -> false;
    }

    @Bean
    BoardUrl boardUrl() {
      return new BoardUrl("8290", "");
    }

    @Bean
    Clock clock() {
      return new FakeClock();
    }
  }

  @LocalServerPort private int port;

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "GET|/state|rebound.example.test|Accept: */*|403",
        "POST|/api/tasks/say|localhost|Origin: http://evil.example.test|403",
        "POST|/api/tasks/say|localhost|Accept: */*|403",
        "POST|/mcp|localhost|X-Working-Directory: /work/t|404",
        "POST|/api/agent/hooks|localhost|X-Working-Directory: /work/t|404",
        "POST|/mcp|localhost|X-Forwarded-For: 10.9.9.9|401",
        "POST|/api/agent/hooks|localhost|X-Forwarded-For: 10.9.9.9|401",
        "POST|/mcp;x=1|localhost|X-Working-Directory: /work/t|400",
        "POST|/api/agent/%2e%2e/tasks|localhost|X-Working-Directory: /work/t|400"
      })
  void answersItsRefusalNotTheLoginRedirect(
      String method, String path, String host, String header, int expected) throws Exception {
    String request =
        method
            + " "
            + path
            + " HTTP/1.1\r\nHost: "
            + host
            + ":"
            + port
            + "\r\n"
            + header
            + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";

    String status;
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
      socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
      status =
          new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
              .readLine();
    }

    assertThat(status).contains(" " + expected);
  }

  @Test
  void answersNotFoundNamingStaleRootTokenWhereClientRegistersWithoutOrigin() throws Exception {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/register"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body())
        .isEqualTo(
            "jagt has no OAuth. A stale root token after a jagt restart sends a client here:"
                + " restart the session.");
  }

  @Test
  void everyResponseCarriesContentSecurityPolicyOfItsOwnOriginOnly() throws Exception {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login.html"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());

    String policy = response.headers().firstValue("Content-Security-Policy").orElse("");
    assertThat(policy)
        .startsWith("default-src 'self'; script-src 'self' 'sha256-")
        .contains("object-src 'none'", "frame-ancestors 'none'")
        .doesNotContain("unsafe-eval", "http:", "https:", "*");
  }

  @Test
  void nextRequestLandsOnLoginPageAfterLogout() throws Exception {
    String base = "http://localhost:" + port;
    HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    client.send(
        HttpRequest.newBuilder(URI.create(base + "/login"))
            .header("Origin", base)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=secret"))
            .build(),
        HttpResponse.BodyHandlers.discarding());
    int signedIn =
        client
            .send(
                HttpRequest.newBuilder(URI.create(base + "/state")).build(),
                HttpResponse.BodyHandlers.discarding())
            .statusCode();

    client.send(
        HttpRequest.newBuilder(URI.create(base + "/logout"))
            .header("Origin", base)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.discarding());
    HttpResponse<Void> afterwards =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/state")).build(),
            HttpResponse.BodyHandlers.discarding());

    assertThat(signedIn).isEqualTo(404);
    assertThat(afterwards.statusCode()).isEqualTo(302);
    assertThat(afterwards.headers().firstValue("Location").orElse("")).endsWith("/login.html");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "POST|/api/interactions|*/*|/",
        "GET|/api/tasks|*/*|/",
        "GET|/state|application/json|/",
        "GET|/state|*/*|/",
        "GET|/favicon.ico|image/avif,image/webp,*/*;q=0.8|/",
        "GET|/state|text/html,*/*;q=0.8|/state?continue"
      })
  void loginReturnsOnlyToPageTheLoggedOutBrowserAsked(
      String method, String path, String accept, String landing) throws Exception {
    String base = "http://localhost:" + port;
    HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    client.send(
        HttpRequest.newBuilder(URI.create(base + path))
            .header("Origin", base)
            .header("Accept", accept)
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.discarding());

    HttpResponse<Void> login =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Origin", base)
                .header("X-Forwarded-For", "10.9.9.7")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=secret"))
                .build(),
            HttpResponse.BodyHandlers.discarding());

    assertThat(login.headers().firstValue("Location")).contains(base + landing);
  }

  @Test
  void refusesPeerThatFailedThePasswordTwiceEvenWithTheRightOne() throws Exception {
    String base = "http://localhost:" + port;
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest.Builder wrong =
        HttpRequest.newBuilder(URI.create(base + "/login"))
            .header("Origin", base)
            .header("X-Forwarded-For", "10.9.9.1")
            .header("Content-Type", "application/x-www-form-urlencoded");
    client.send(
        wrong.POST(HttpRequest.BodyPublishers.ofString("username=owner&password=nope")).build(),
        HttpResponse.BodyHandlers.discarding());
    client.send(
        wrong.POST(HttpRequest.BodyPublishers.ofString("username=owner&password=nope")).build(),
        HttpResponse.BodyHandlers.discarding());

    HttpResponse<Void> third =
        client.send(
            wrong
                .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=secret"))
                .build(),
            HttpResponse.BodyHandlers.discarding());

    assertThat(third.statusCode()).isEqualTo(429);
    assertThat(third.headers().firstValue("Retry-After")).contains("301");
  }

  @Test
  void backsOffOnlyTheForwardedClientBehindOneIngress() throws Exception {
    String base = "http://localhost:" + port;
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest.Builder attacker =
        HttpRequest.newBuilder(URI.create(base + "/login"))
            .header("Origin", base)
            .header("X-Forwarded-For", "10.9.9.5")
            .header("Content-Type", "application/x-www-form-urlencoded");
    for (int i = 0; i < 2; i++) {
      client.send(
          attacker
              .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=nope"))
              .build(),
          HttpResponse.BodyHandlers.discarding());
    }

    HttpResponse<Void> other =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Origin", base)
                .header("X-Forwarded-For", "10.9.9.6")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=secret"))
                .build(),
            HttpResponse.BodyHandlers.discarding());

    assertThat(other.statusCode()).isEqualTo(302);
  }

  @Test
  void sendsWrongPasswordBackToTheLoginPageWithTheError() throws Exception {
    String base = "http://localhost:" + port;

    HttpResponse<Void> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(base + "/login"))
                    .header("Origin", base)
                    .header("X-Forwarded-For", "10.9.9.2")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=nope"))
                    .build(),
                HttpResponse.BodyHandlers.discarding());

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/login.html?error");
  }

  @Test
  void sendsRightPasswordToTheBoard() throws Exception {
    String base = "http://localhost:" + port;

    HttpResponse<Void> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(base + "/login"))
                    .header("Origin", base)
                    .header("X-Forwarded-For", "10.9.9.3")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=secret"))
                    .build(),
                HttpResponse.BodyHandlers.discarding());

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/");
  }

  @Test
  void forgetsPeersFailureWhenItLogsInBeforeTheLimit() throws Exception {
    String base = "http://localhost:" + port;
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest.Builder login =
        HttpRequest.newBuilder(URI.create(base + "/login"))
            .header("Origin", base)
            .header("X-Forwarded-For", "10.9.9.4")
            .header("Content-Type", "application/x-www-form-urlencoded");
    client.send(
        login.POST(HttpRequest.BodyPublishers.ofString("username=owner&password=nope")).build(),
        HttpResponse.BodyHandlers.discarding());
    client.send(
        login.POST(HttpRequest.BodyPublishers.ofString("username=owner&password=secret")).build(),
        HttpResponse.BodyHandlers.discarding());
    client.send(
        login.POST(HttpRequest.BodyPublishers.ofString("username=owner&password=nope")).build(),
        HttpResponse.BodyHandlers.discarding());

    HttpResponse<Void> next =
        client.send(
            login
                .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=secret"))
                .build(),
            HttpResponse.BodyHandlers.discarding());

    assertThat(next.statusCode()).isEqualTo(302);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Origin: http://localhost:%d|401",
        "Accept: */*|403",
      })
  void refusesTheStreamOpenedWithoutLoginOrWithoutThePagesOrigin(String header, int status)
      throws Exception {
    String request =
        "GET /api/windows/stream?task=ABC-1 HTTP/1.1\r\nHost: localhost:"
            + port
            + "\r\n"
            + header.formatted(port)
            + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n";

    String line;
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
      socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
      line =
          new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
              .readLine();
    }

    assertThat(line).contains(" " + status);
  }

  @Test
  void letsSignedInHumanOpenTheStreamAndClosesItWhereNoWindowHoldsTheSession() throws Exception {
    String base = "http://localhost:" + port;
    CookieManager cookies = new CookieManager();
    HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
    client.send(
        HttpRequest.newBuilder(URI.create(base + "/login"))
            .header("Origin", base)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=owner&password=secret"))
            .build(),
        HttpResponse.BodyHandlers.discarding());
    String session =
        cookies.getCookieStore().getCookies().stream()
            .map(HttpCookie::toString)
            .collect(Collectors.joining("; "));
    AtomicInteger closedWith = new AtomicInteger();
    CountDownLatch closed = new CountDownLatch(1);

    client
        .newWebSocketBuilder()
        .header("Origin", base)
        .header("Cookie", session)
        .buildAsync(
            URI.create("ws://localhost:" + port + "/api/windows/stream?task=ABC-1"),
            new WebSocket.Listener() {
              @Override
              public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
                closedWith.set(code);
                closed.countDown();
                return CompletableFuture.completedFuture(null);
              }
            })
        .join();

    closed.await();

    assertThat(closedWith).hasValue(4404);
  }
}

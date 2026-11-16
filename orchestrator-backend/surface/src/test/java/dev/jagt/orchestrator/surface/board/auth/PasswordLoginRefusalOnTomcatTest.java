package dev.jagt.orchestrator.surface.board.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.surface.board.filter.BoardWriteFilter;
import dev.jagt.orchestrator.surface.board.filter.LoopbackFilter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
      "orchestrator.auth.passwordHash=$2y$04$.W6F.lVT3xhLTvtg8hW1WuEf2QP.enJ4zNrFWqlXmuBhzZndQnIiu"
    })
@MockitoBean(types = {UserNotifier.class})
class PasswordLoginRefusalOnTomcatTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableConfigurationProperties(AuthProperties.class)
  @Import({PasswordLogin.class, LoopbackFilter.class, BoardWriteFilter.class})
  static class Board {
    @Bean
    BoardUrl boardUrl() {
      return new BoardUrl("8290", "");
    }
  }

  @LocalServerPort private int port;

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "GET|/state|rebound.example.test|Accept: */*",
        "POST|/api/tasks/say|localhost|Origin: http://evil.example.test",
        "POST|/api/tasks/say|localhost|Accept: */*"
      })
  void answersItsRefusalNotTheLoginRedirect(String method, String path, String host, String header)
      throws Exception {
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

    assertThat(status).contains(" 403");
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
}

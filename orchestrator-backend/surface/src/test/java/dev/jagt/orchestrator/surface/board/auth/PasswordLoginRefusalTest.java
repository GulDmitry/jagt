package dev.jagt.orchestrator.surface.board.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.surface.board.filter.BoardWriteFilter;
import dev.jagt.orchestrator.surface.board.filter.LoopbackFilter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    classes = PasswordLoginRefusalTest.Board.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "orchestrator.auth.mode=password",
      "orchestrator.auth.passwordHash=$2y$04$cpM1ODse0DnRCsm1Zpa9quQKnFm0ZxoiaduZfAt4nnLFQp0rBW332"
    })
class PasswordLoginRefusalTest {

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
      nullValues = "none",
      value = {
        "GET|/state|rebound.example.test|none",
        "POST|/api/tasks/say|localhost|http://evil.example.test",
        "POST|/api/tasks/say|localhost|none"
      })
  void answersItsRefusalNotTheLoginRedirect(String method, String path, String host, String origin)
      throws Exception {
    String originHeader = origin == null ? "" : "Origin: " + origin + "\r\n";

    String status =
        statusLine(
            method
                + " "
                + path
                + " HTTP/1.1\r\nHost: "
                + host
                + ":"
                + port
                + "\r\n"
                + originHeader
                + "Content-Length: 0\r\nConnection: close\r\n\r\n");

    assertThat(status).contains(" 403");
  }

  private String statusLine(String request) throws Exception {
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
      socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
      socket.getOutputStream().flush();
      return new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
          .readLine();
    }
  }
}

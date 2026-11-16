package dev.jagt.orchestrator.surface.board.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.channel.UserNotifier;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = PasswordLoginRefusalTest.Board.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "orchestrator.auth.mode=password",
      "orchestrator.auth.passwordHash=$2y$04$cpM1ODse0DnRCsm1Zpa9quQKnFm0ZxoiaduZfAt4nnLFQp0rBW332"
    })
@MockitoBean(types = {UserNotifier.class})
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
}

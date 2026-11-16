package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandBoardTest {

  private final List<String> logins = new CopyOnWriteArrayList<>();
  private final List<Integer> answers = new CopyOnWriteArrayList<>();
  private final AtomicInteger issued = new AtomicInteger();
  private final AtomicInteger valid = new AtomicInteger();
  private HttpServer server;
  private String base;

  @BeforeEach
  void startBoard() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/login",
        exchange -> {
          try (exchange) {
            String form =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            logins.add(form);
            if (form.endsWith("secret")) {
              valid.set(issued.incrementAndGet());
              exchange
                  .getResponseHeaders()
                  .add("Set-Cookie", "SESSION=s" + issued.get() + "; Path=/");
              exchange.sendResponseHeaders(302, -1);
            } else {
              exchange.sendResponseHeaders(401, -1);
            }
          }
        });
    server.createContext(
        "/state",
        exchange -> {
          try (exchange) {
            boolean signedIn =
                exchange
                    .getRequestHeaders()
                    .getOrDefault("Cookie", List.of())
                    .contains("SESSION=s" + valid.get());
            int status = signedIn ? 200 : 302;
            answers.add(status);
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, signedIn ? body.length : -1);
            if (signedIn) {
              exchange.getResponseBody().write(body);
            }
          }
        });
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopBoard() {
    server.stop(0);
  }

  @Test
  void logsInOnceAndCarriesTheCookie() {
    StandBoard board = new StandBoard(base, "secret");

    board.state();
    board.state();

    assertThat(logins).containsExactly("username=owner&password=secret");
    assertThat(answers).containsExactly(200, 200);
  }

  @Test
  void isNotReadyWithoutLogin() {
    StandBoard board = new StandBoard(base, "");

    assertThat(board.ready()).isFalse();
  }

  @Test
  void logsInAgainWhenTheSessionIsGoneAfterRestart() {
    StandBoard board = new StandBoard(base, "secret");
    board.state();
    valid.set(0);

    assertThat(board.ready()).isTrue();
    assertThat(logins).hasSize(2);
  }

  @Test
  void wrongPasswordFailsTheLogin() {
    StandBoard board = new StandBoard(base, "wrong");

    assertThatThrownBy(board::state)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("login at " + base + " answered 401");
  }
}

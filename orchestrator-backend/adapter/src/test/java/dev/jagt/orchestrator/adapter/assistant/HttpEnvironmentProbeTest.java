package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HttpEnvironmentProbeTest {

  private final HttpEnvironmentProbe probe = new HttpEnvironmentProbe(HttpClient.newHttpClient());
  private final int[] status = {200};
  private HttpServer server;

  @BeforeEach
  void listen() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(status[0], -1);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @ParameterizedTest
  @CsvSource({"401,true", "503,false", "500,false"})
  void answersOnlyWhereTheEnvironmentRespondsBelowServerFault(int respondedWith, boolean answered) {
    status[0] = respondedWith;

    assertThat(probe.answers("http://127.0.0.1:" + server.getAddress().getPort() + "/"))
        .isEqualTo(answered);
  }

  @Test
  void doesNotAnswerWhereNothingListens() {
    int port = server.getAddress().getPort();
    server.stop(0);

    assertThat(probe.answers("http://127.0.0.1:" + port + "/")).isFalse();
  }

  @Test
  void doesNotAnswerAnAddressThatIsNoUrl() {
    assertThat(probe.answers("not a url")).isFalse();
  }

  @Test
  void doesNotAnswerAndKeepsItsInterruptWhereTheProbingThreadWasInterrupted() {
    Thread.currentThread().interrupt();

    boolean answered = probe.answers("http://127.0.0.1:" + server.getAddress().getPort() + "/");

    assertThat(answered).isFalse();
    assertThat(Thread.interrupted()).isTrue();
  }
}

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class StandBoardVerbsTest {

  private final List<String> seen = new CopyOnWriteArrayList<>();
  private HttpServer server;
  private String base;

  @BeforeEach
  void startBoard() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try (exchange) {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().toString();
            seen.add(exchange.getRequestMethod() + " " + path + " " + body);
            int status = path.contains("refused") ? 500 : 200;
            byte[] answer = "{\"tasks\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
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
  void openingTaskPostsItsRefProjectAndMode() {
    StandBoard board = new StandBoard(base, "");

    JsonNode answer = board.open("ABC-42", "proj", "plan");

    assertThat(answer.has("tasks")).isTrue();
    assertThat(seen)
        .containsExactly(
            "POST /api/tasks {\"ref\":\"ABC-42\",\"project\":\"proj\",\"mode\":\"plan\"}");
  }

  @Test
  void pressingVerbPostsItToTheTask() {
    StandBoard board = new StandBoard(base, "");

    board.press("ABC-42", "ship");

    assertThat(seen).containsExactly("POST /api/tasks/actions/ship?task=ABC-42 {}");
  }

  @Test
  void sayingLinePostsItToTheTask() {
    StandBoard board = new StandBoard(base, "");

    board.say("ABC-42", "wait");

    assertThat(seen).containsExactly("POST /api/tasks/say?task=ABC-42 {\"line\":\"wait\"}");
  }

  @Test
  void runningJobPostsItsName() {
    StandBoard board = new StandBoard(base, "");

    board.run("verify");

    assertThat(seen).containsExactly("POST /api/commands/run?about=verify {}");
  }

  @Test
  void runningJobThatIsRefusedIsNotFailure() {
    StandBoard board = new StandBoard(base, "");

    board.run("refused");

    assertThat(seen).hasSize(1);
  }

  @Test
  void pressingVerbThatIsRefusedFailsWithTheStatus() {
    StandBoard board = new StandBoard(base, "");

    assertThatThrownBy(() -> board.press("refused", "ship"))
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("answered 500");
  }

  @Test
  void theTaskListIsReadFromTheApi() {
    StandBoard board = new StandBoard(base, "");

    JsonNode tasks = board.tasks();

    assertThat(tasks.path("tasks")).isEmpty();
    assertThat(seen).containsExactly("GET /api/tasks ");
  }

  @Test
  void boardNobodyListensOnIsNotReady() {
    server.stop(0);
    StandBoard board = new StandBoard(base, "");

    assertThat(board.ready()).isFalse();
  }

  @Test
  void boardNobodyListensOnFailsReadWithTheCause() {
    server.stop(0);
    StandBoard board = new StandBoard(base, "");

    assertThatThrownBy(board::state)
        .isInstanceOf(StandFailure.class)
        .hasMessageContaining("did not answer");
  }
}

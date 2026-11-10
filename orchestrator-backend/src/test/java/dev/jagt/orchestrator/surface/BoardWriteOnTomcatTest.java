package dev.jagt.orchestrator.surface;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.port.UserNotifier;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
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
      "orchestrator.usage-limits.enabled=false"
    })
@ResourceLock("spring-logging")
class BoardWriteOnTomcatTest {

  @MockitoBean private UserNotifier userNotifier;

  @TempDir static Path root;

  @MockitoBean private EditorDriver editorDriver;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void keepConfigAndStateInTheTempRoot(DynamicPropertyRegistry registry) throws IOException {
    ShippedRoot.configure(registry, root);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api;x/tasks/actions/deploy?task=ABC-42",
        "/%61pi/tasks/actions/deploy?task=ABC-42"
      })
  void refusesBoardWriteWithoutAnOriginHoweverItsPathIsSpelled(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(403);
  }
}

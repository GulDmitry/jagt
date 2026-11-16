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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.import=",
      "orchestrator.terminal=none",
      "orchestrator.startup-checks=false",
      "orchestrator.usage-limits.enabled=false"
    })
class BoardFolderTest {

  @MockitoBean private UserNotifier userNotifier;

  @MockitoBean private EditorDriver editorDriver;

  @TempDir static Path root;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void servesTheBoardFromTempFolder(DynamicPropertyRegistry registry) throws IOException {
    ShippedRoot.configure(registry, root);
    Files.createDirectory(root.resolve("my-board"));
    Files.writeString(root.resolve("my-board").resolve("probe.html"), "<p>edited</p>");
    registry.add("orchestrator.board.dir", () -> "my-board");
  }

  @Test
  void servesTheFileFromTheFolderBoardDirNamesRelativeToTheRootAndShowsAnEditOnReload()
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/probe.html")).build();
    HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> first = client.send(request, HttpResponse.BodyHandlers.ofString());
    Files.writeString(root.resolve("my-board").resolve("probe.html"), "<p>edited again</p>");
    HttpResponse<String> reloaded = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(first.body()).isEqualTo("<p>edited</p>");
    assertThat(reloaded.body()).isEqualTo("<p>edited again</p>");
  }
}

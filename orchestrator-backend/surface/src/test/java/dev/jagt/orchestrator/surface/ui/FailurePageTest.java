package dev.jagt.orchestrator.surface.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FailurePageTest {

  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void listsEveryProblemAndTheFileToEdit(@TempDir Path dir) throws Exception {
    Path config = Files.writeString(dir.resolve("jagt.yml"), "orchestrator:\n  platform: linux\n");

    try (FailurePage page = FailurePage.listen(0, config, "1. git <missing>\n2. no projects").get()) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + page.port() + "/")).build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.body())
          .contains("1. git &lt;missing&gt;")
          .contains("2. no projects")
          .contains("platform: linux");
    }
  }

  @Test
  void writesTheFileKeepsTheOldOneAndReleasesTheWaitWhenSaved(@TempDir Path dir) throws Exception {
    Path config = Files.writeString(dir.resolve("jagt.yml"), "old: 1\n");

    try (FailurePage page = FailurePage.listen(0, config, "problem").get()) {
      client.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + page.port() + "/save"))
              .POST(HttpRequest.BodyPublishers.ofString("yml=new%3A+2%0D%0A"))
              .build(),
          HttpResponse.BodyHandlers.ofString());
      page.awaitSave();

      assertThat(config).hasContent("new: 2\n");
      assertThat(dir.resolve("jagt.yml.bak")).hasContent("old: 1\n");
    }
  }

  @Test
  void refusesASaveFromAnotherOrigin(@TempDir Path dir) throws Exception {
    Path config = Files.writeString(dir.resolve("jagt.yml"), "old: 1\n");

    try (FailurePage page = FailurePage.listen(0, config, "problem").get()) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + page.port() + "/save"))
                  .header("Origin", "http://evil.example")
                  .timeout(Duration.ofSeconds(5))
                  .POST(HttpRequest.BodyPublishers.ofString("yml=pwned"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(403);
      assertThat(config).hasContent("old: 1\n");
    }
  }

  @Test
  void isNotServedWhereThePortIsTaken(@TempDir Path dir) throws Exception {
    try (FailurePage first = FailurePage.listen(0, dir.resolve("jagt.yml"), "problem").get()) {
      assertThat(FailurePage.listen(first.port(), dir.resolve("jagt.yml"), "problem")).isEmpty();
    }
  }
}

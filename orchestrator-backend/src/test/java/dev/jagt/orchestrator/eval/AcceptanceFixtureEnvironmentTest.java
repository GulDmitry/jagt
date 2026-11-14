package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcceptanceFixtureEnvironmentTest {

  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void recordedCallAnswersWithItsRecordedStatusAndBody() throws Exception {
    try (AcceptanceFixtureEnvironment environment =
        AcceptanceFixtureEnvironment.serve(
            Map.of(
                "http",
                Map.of(
                    "POST /quotes", Map.of("status", 201, "body", Map.of("id", "sample-q1")))))) {
      HttpResponse<String> answer =
          client.send(
              HttpRequest.newBuilder(URI.create(environment.url() + "/quotes"))
                  .method("POST", HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(answer.statusCode()).isEqualTo(201);
      assertThat(answer.body()).contains("sample-q1");
    }
  }

  @Test
  void callNoRecordingHoldsFailsAsMissingPage() throws Exception {
    try (AcceptanceFixtureEnvironment environment = AcceptanceFixtureEnvironment.serve(Map.of())) {
      assertThat(
              client
                  .send(
                      HttpRequest.newBuilder(URI.create(environment.url() + "/anything"))
                          .method("GET", HttpRequest.BodyPublishers.noBody())
                          .build(),
                      HttpResponse.BodyHandlers.ofString())
                  .statusCode())
          .isEqualTo(404);
    }
  }

  @Test
  void roleWithNoSavedCredentialIsRefusedItsSignIn() throws Exception {
    try (AcceptanceFixtureEnvironment environment =
        AcceptanceFixtureEnvironment.serve(
            Map.of("login", Map.of("insurer-role", "empty form, no saved credential")))) {
      HttpResponse<String> answer =
          client.send(
              HttpRequest.newBuilder(URI.create(environment.url() + "/login/insurer-role"))
                  .method("GET", HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(answer.statusCode()).isEqualTo(401);
      assertThat(answer.body()).contains("no saved credential");
    }
  }

  @Test
  void liveEnvironmentIsHandedOnAsItIsWithNoServerStarted() {
    try (AcceptanceFixtureEnvironment environment =
        AcceptanceFixtureEnvironment.serve(Map.of("live", "https://app.example.test"))) {
      assertThat(environment.url()).isEqualTo("https://app.example.test");
    }
  }

  @Test
  void recordedPageAnswersItsFieldsAsJson() throws Exception {
    try (AcceptanceFixtureEnvironment environment =
        AcceptanceFixtureEnvironment.serve(
            Map.of("ui", Map.of("/contracts/sample-1", Map.of("renewalDate", "01.07.2027"))))) {
      assertThat(
              client
                  .send(
                      HttpRequest.newBuilder(URI.create(environment.url() + "/contracts/sample-1"))
                          .method("GET", HttpRequest.BodyPublishers.noBody())
                          .build(),
                      HttpResponse.BodyHandlers.ofString())
                  .body())
          .contains("01.07.2027");
    }
  }
}

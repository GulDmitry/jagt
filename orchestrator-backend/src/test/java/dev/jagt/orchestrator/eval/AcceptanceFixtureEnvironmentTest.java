package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcceptanceFixtureEnvironmentTest {

  private static HttpResponse<String> call(String method, String url) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create(url))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void aRecordedCallAnswersWithItsRecordedStatusAndBody() throws Exception {
    try (AcceptanceFixtureEnvironment environment =
        AcceptanceFixtureEnvironment.serve(
            Map.of(
                "http",
                Map.of(
                    "POST /quotes", Map.of("status", 201, "body", Map.of("id", "sample-q1")))))) {
      HttpResponse<String> answer = call("POST", environment.url() + "/quotes");

      assertThat(answer.statusCode()).isEqualTo(201);
      assertThat(answer.body()).contains("sample-q1");
    }
  }

  @Test
  void aCallNoRecordingHoldsFailsAsAMissingPage() throws Exception {
    try (AcceptanceFixtureEnvironment environment = AcceptanceFixtureEnvironment.serve(Map.of())) {
      assertThat(call("GET", environment.url() + "/anything").statusCode()).isEqualTo(404);
    }
  }

  @Test
  void aRoleWithNoSavedCredentialIsRefusedItsSignIn() throws Exception {
    try (AcceptanceFixtureEnvironment environment =
        AcceptanceFixtureEnvironment.serve(
            Map.of("login", Map.of("insurer-role", "empty form, no saved credential")))) {
      HttpResponse<String> answer = call("GET", environment.url() + "/login/insurer-role");

      assertThat(answer.statusCode()).isEqualTo(401);
      assertThat(answer.body()).contains("no saved credential");
    }
  }

  @Test
  void aRecordedPageAnswersItsFieldsAsJson() throws Exception {
    try (AcceptanceFixtureEnvironment environment =
        AcceptanceFixtureEnvironment.serve(
            Map.of("ui", Map.of("/contracts/sample-1", Map.of("renewalDate", "01.07.2027"))))) {
      assertThat(call("GET", environment.url() + "/contracts/sample-1").body())
          .contains("01.07.2027");
    }
  }
}

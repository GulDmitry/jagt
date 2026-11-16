package dev.jagt.orchestrator.surface.board.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.surface.board.filter.BoardWriteFilter;
import dev.jagt.orchestrator.surface.board.filter.LoopbackFilter;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(
    classes = OidcLoginOnTomcatTest.Board.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "orchestrator.auth.mode=oidc",
      "orchestrator.auth.oidc.clientId=jagt",
      "orchestrator.auth.oidc.clientSecretEnv=SSO_SECRET",
      "orchestrator.auth.oidc.allowed=owner-sub",
      "SSO_SECRET=s3cret"
    })
class OidcLoginOnTomcatTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableConfigurationProperties({AuthProperties.class, OidcProperties.class})
  @Import({OidcLogin.class, LoopbackFilter.class, BoardWriteFilter.class})
  static class Board {
    @Bean
    RouterFunction<ServerResponse> state() {
      return RouterFunctions.route()
          .GET("/state", request -> ServerResponse.ok().body("board"))
          .build();
    }

    @Bean
    BoardUrl boardUrl() {
      return new BoardUrl("8290", "");
    }
  }

  private static final FakeOidcProvider PROVIDER = new FakeOidcProvider("jagt");

  @DynamicPropertySource
  static void issuer(DynamicPropertyRegistry registry) {
    registry.add("orchestrator.auth.oidc.issuerUri", PROVIDER::issuer);
  }

  @AfterAll
  static void stopProvider() {
    PROVIDER.stop();
  }

  @MockitoBean UserNotifier notifier;

  @LocalServerPort private int port;

  @ParameterizedTest
  @CsvSource({"owner-sub,302,200", "other-sub,403,302"})
  void opensTheBoardOnlyForTheAllowedSubject(String subject, int callbackStatus, int boardStatus)
      throws Exception {
    HttpClient browser =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    String board = "http://localhost:" + port;

    HttpResponse<String> start =
        browser.send(
            HttpRequest.newBuilder(URI.create(board + "/oauth2/authorization/oidc")).build(),
            HttpResponse.BodyHandlers.ofString());
    MultiValueMap<String, String> authorize =
        UriComponentsBuilder.fromUriString(start.headers().firstValue("Location").orElseThrow())
            .build()
            .getQueryParams();
    PROVIDER.expectLogin(
        subject, URLDecoder.decode(authorize.getFirst("nonce"), StandardCharsets.UTF_8));
    HttpResponse<String> callback =
        browser.send(
            HttpRequest.newBuilder(
                    URI.create(
                        board
                            + "/login/oauth2/code/oidc?code="
                            + subject
                            + "&state="
                            + authorize.getFirst("state")))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> page =
        browser.send(
            HttpRequest.newBuilder(URI.create(board + "/state")).build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(callback.statusCode()).isEqualTo(callbackStatus);
    assertThat(page.statusCode()).isEqualTo(boardStatus);
  }
}

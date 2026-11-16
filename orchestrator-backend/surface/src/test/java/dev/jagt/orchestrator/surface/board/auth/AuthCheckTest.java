package dev.jagt.orchestrator.surface.board.auth;

import static dev.jagt.orchestrator.surface.board.auth.TestAuthCheckBuilder.authCheck;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.service.log.BoardUrl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AuthCheckTest {

  @Test
  void refusesTheStartOfBoardBeyondLoopbackWithNoLogin() {
    List<Problem> problems =
        authCheck().withAddress("0.0.0.0").build(new BoardUrl("8290", "")).problems();

    assertThat(problems).hasSize(1);
    assertThat(problems.get(0).key()).isEqualTo("orchestrator.auth");
    assertThat(problems.get(0).text()).contains("orchestrator.auth", "password");
  }

  @Test
  void startsLoopbackBoardWithNoLogin() {
    assertThat(authCheck().build(new BoardUrl("8290", "")).problems()).isEmpty();
  }

  @Test
  void refusesHostAllowListBehindNoLogin() {
    assertThat(
            authCheck()
                .withAllowedHosts(List.of("jagt.example.test"))
                .build(new BoardUrl("8290", ""))
                .problems())
        .hasSize(1);
  }

  @Test
  void refusesPublicUrlNamingHostBehindNoLogin() {
    assertThat(authCheck().build(new BoardUrl("8290", "https://jagt.example.com")).problems())
        .hasSize(1);
  }

  @Test
  void startsLoopbackPublicUrlWithNoLogin() {
    assertThat(authCheck().build(new BoardUrl("8290", "http://localhost:8290")).problems())
        .isEmpty();
  }

  @Test
  void startsBoardBeyondLoopbackWithPasswordHash() {
    AuthCheck check =
        authCheck()
            .withAddress("0.0.0.0")
            .withMode(AuthProperties.Mode.PASSWORD)
            .withPasswordHash("$2y$04$cpM1ODse0DnRCsm1Zpa9quQKnFm0ZxoiaduZfAt4nnLFQp0rBW332")
            .build(new BoardUrl("8290", ""));

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void refusesPasswordThatIsNoBcryptHashWithoutEchoingIt() {
    AuthCheck check =
        authCheck()
            .withMode(AuthProperties.Mode.PASSWORD)
            .withPasswordHash("hunter2-plain")
            .build(new BoardUrl("8290", ""));

    List<Problem> problems = check.problems();

    assertThat(problems).hasSize(1);
    assertThat(problems.get(0).text()).doesNotContain("hunter2-plain");
    assertThat(new AuthProperties(AuthProperties.Mode.PASSWORD, "hunter2-plain", List.of()))
        .hasToString("AuthProperties[mode=PASSWORD]");
  }

  @Test
  void startsOidcWhenEverySettingIsGivenAndTheSecretVariableIsSet() {
    AuthCheck check =
        authCheck()
            .withAddress("0.0.0.0")
            .withMode(AuthProperties.Mode.OIDC)
            .withOidc(new OidcProperties("https://sso.example.test", "jagt", "SSO_SECRET", "owner"))
            .withEnvironment(Map.of("SSO_SECRET", "s3cret"))
            .build(new BoardUrl("8290", ""));

    assertThat(check.problems()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      nullValues = "none",
      value = {
        "none|jagt|SSO_SECRET|owner|orchestrator.auth.oidc.issuerUri",
        "https://sso.example.test|none|SSO_SECRET|owner|orchestrator.auth.oidc.clientId",
        "https://sso.example.test|jagt|none|owner|orchestrator.auth.oidc.clientSecretEnv",
        "https://sso.example.test|jagt|SSO_SECRET|none|orchestrator.auth.oidc.allowed"
      })
  void refusesOidcWithMissingSetting(
      String issuer, String clientId, String secretEnv, String allowed, String key) {
    AuthCheck check =
        authCheck()
            .withMode(AuthProperties.Mode.OIDC)
            .withOidc(new OidcProperties(issuer, clientId, secretEnv, allowed))
            .withEnvironment(Map.of("SSO_SECRET", "s3cret"))
            .build(new BoardUrl("8290", ""));

    assertThat(check.problems()).extracting(Problem::key).containsExactly(key);
  }

  @Test
  void refusesOidcWhoseSecretVariableIsNotSet() {
    AuthCheck check =
        authCheck()
            .withMode(AuthProperties.Mode.OIDC)
            .withOidc(new OidcProperties("https://sso.example.test", "jagt", "SSO_SECRET", "owner"))
            .build(new BoardUrl("8290", ""));

    assertThat(check.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.auth.oidc.clientSecretEnv");
  }
}

package dev.jagt.orchestrator.surface.board.auth;

import static dev.jagt.orchestrator.surface.board.auth.TestAuthCheckBuilder.authCheck;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.service.log.BoardUrl;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}

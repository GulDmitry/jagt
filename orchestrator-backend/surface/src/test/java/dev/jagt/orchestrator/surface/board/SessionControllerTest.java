package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.surface.board.auth.AuthProperties;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SessionControllerTest {

  @ParameterizedTest
  @CsvSource({"NONE, false", "PASSWORD, true", "OIDC, true"})
  void saysTheBoardSitsBehindLoginOnlyWhenAuthIsOn(AuthProperties.Mode mode, boolean login) {
    SessionController controller = new SessionController(new AuthProperties(mode, null, List.of()));

    assertThat(controller.session().login()).isEqualTo(login);
  }
}

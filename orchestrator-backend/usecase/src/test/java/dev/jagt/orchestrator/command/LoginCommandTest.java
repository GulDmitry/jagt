package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ReachLedger;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginCommandTest {

  private final ReachLedger reach = new ReachLedger();
  private final SessionHost sessions = mock(SessionHost.class);
  private final ConfigService config = mock(ConfigService.class);
  private final LoginCommand login = new LoginCommand(reach, sessions, config);

  @Test
  void opensTheFixAsTheProcessOfAWindowOfItsOwn() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(sessions.sessionName(any())).thenReturn("jagt");
    reach.lost(
        new ReachLost(
            "glab",
            "git.example.invalid",
            List.of("glab", "auth", "login", "--hostname", "git.example.invalid"),
            "glab not logged in"));

    login.run("");

    verify(sessions)
        .openCommandWindow(
            "jagt",
            "jagt",
            "login",
            List.of("glab", "auth", "login", "--hostname", "git.example.invalid"));
  }

  @Test
  void opensNothingWhereTheLostReachHasNoCommandToFixIt() {
    reach.lost(new ReachLost("glab", "git.example.invalid", List.of(), "glab is not installed"));

    assertThatThrownBy(() -> login.run("")).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(sessions);
  }

  @Test
  void opensNothingWhereNoReachIsLost() {
    assertThatThrownBy(() -> login.run("")).isInstanceOf(IllegalArgumentException.class);
    assertThat(reach.standing()).isEmpty();
    verifyNoInteractions(sessions);
  }
}

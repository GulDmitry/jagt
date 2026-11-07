package dev.jagt.orchestrator.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Processes;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClaudeKeychainTest {

  private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

  @Test
  void answersNothingSoTheFileIsReadWhenTheKeychainHoldsNoItem() {
    Processes processes = mock(Processes.class);
    when(processes.run(any(), any(), anyList()))
        .thenReturn(new Processes.Result(44, "", "item could not be found"));

    var credential = new ClaudeKeychain(processes).read(NOW);

    assertThat(credential).isEmpty();
  }

  @Test
  void answersFailedRatherThanNoSubscriptionWhenTheKeychainRefused() {
    Processes processes = mock(Processes.class);
    when(processes.run(any(), any(), anyList()))
        .thenReturn(new Processes.Result(51, "", "user interaction is not allowed"));

    var credential = new ClaudeKeychain(processes).read(NOW);

    assertThat(credential)
        .contains(
            new OauthCredential.Failed("keychain read exited 51 user interaction is not allowed"));
  }

  @Test
  void takesTheKeychainLoginWhenItHoldsOne() {
    Processes processes = mock(Processes.class);
    when(processes.run(any(), any(), anyList()))
        .thenReturn(
            new Processes.Result(
                0, "{\"claudeAiOauth\": {\"accessToken\": \"tok-keychain\"}}", ""));

    var credential = new ClaudeKeychain(processes).read(NOW);

    assertThat(credential).contains(new OauthCredential.Token("tok-keychain", ""));
  }

  @Test
  void answersFailedWhenTheKeychainCannotBeRun() {
    Processes processes = mock(Processes.class);
    when(processes.run(any(), any(), anyList()))
        .thenThrow(new IllegalStateException("security not found"));

    var credential = new ClaudeKeychain(processes).read(NOW);

    assertThat(credential)
        .contains(new OauthCredential.Failed("keychain read failed security not found"));
  }
}

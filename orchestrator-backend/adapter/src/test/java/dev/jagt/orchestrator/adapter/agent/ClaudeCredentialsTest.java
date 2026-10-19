package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeCredentialsTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void takesTheAccessTokenOfAStoredSubscriptionLogin() {
        var credential = ClaudeCredentials.parse("""
                {"claudeAiOauth": {"accessToken": "tok-abc", "expiresAt": 1791900000000}}
                """, NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Token("tok-abc", ""));
    }

    @Test
    void answersFailedNamingTheRefreshWhenTheTokenExpired() {
        var credential = ClaudeCredentials.parse("""
                {"claudeAiOauth": {"accessToken": "tok-abc", "expiresAt": 1700000000000}}
                """, NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Failed(
                "token expired at 2023-11-14T22:13:20Z, running claude refreshes it"));
    }

    @Test
    void answersNoSubscriptionWhereTheLoginHoldsNoOauthToken() {
        var credential = ClaudeCredentials.parse("{\"primaryApiKey\": \"key-abc\"}", NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.None());
    }

    @Test
    void neverQuotesTheStoredLoginWhenItIsNotJson() {
        var credential = ClaudeCredentials.parse("tok-abc-not-json", NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Failed("stored login is not JSON"));
    }

    @Test
    void fallsBackToTheFileWhenTheKeychainHoldsNoItem(@TempDir Path home) throws Exception {
        Processes processes = mock(Processes.class);
        when(processes.run(any(), any(), anyList()))
                .thenReturn(new Processes.Result(44, "", "item could not be found"));
        Files.createDirectories(home.resolve(".claude"));
        Files.writeString(home.resolve(".claude/.credentials.json"),
                "{\"claudeAiOauth\": {\"accessToken\": \"tok-file\"}}");
        Host host = mock(Host.class);
        when(host.home()).thenReturn(home);

        var credential = new ClaudeCredentials(processes, host, "macos").read(NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Token("tok-file", ""));
    }

    @Test
    void answersFailedRatherThanNoSubscriptionWhenTheKeychainRefused(@TempDir Path home) {
        Processes processes = mock(Processes.class);
        when(processes.run(any(), any(), anyList()))
                .thenReturn(new Processes.Result(51, "", "user interaction is not allowed"));

        Host host = mock(Host.class);
        when(host.home()).thenReturn(home);

        var credential = new ClaudeCredentials(processes, host, "macos").read(NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Failed(
                "keychain read exited 51 user interaction is not allowed"));
    }
}

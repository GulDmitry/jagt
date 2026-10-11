package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CodexCredentialsTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void takesTheAccountFromTheTokensOwnClaimWhereTheLoginNamesNone() {
        var credential = CodexCredentials.parse("""
                {"OPENAI_API_KEY": null,
                 "tokens": {"access_token": "h.eyJleHAiOjE4OTM0NTYwMDAsImh0dHBzOi8vYXBpLm9wZW5haS5jb20vYXV0aCI6eyJjaGF0Z3B0X2FjY291bnRfaWQiOiJhY2N0LTQyIn19.s"}}
                """, NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Token(
                "h.eyJleHAiOjE4OTM0NTYwMDAsImh0dHBzOi8vYXBpLm9wZW5haS5jb20vYXV0aCI6eyJjaGF0Z3B0X2FjY291bnRfaWQiOiJhY2N0LTQyIn19.s",
                "acct-42"));
    }

    @Test
    void answersNoSubscriptionForAnApiKeyLogin() {
        var credential = CodexCredentials.parse("{\"OPENAI_API_KEY\": \"key-abc\", \"tokens\": null}", NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.None());
    }

    @Test
    void answersFailedNamingTheRefreshWhenTheTokenExpired() {
        var credential = CodexCredentials.parse("""
                {"tokens": {"access_token": "h.eyJleHAiOjE3MDAwMDAwMDB9.s", "account_id": "acct-42"}}
                """, NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Failed(
                "token expired at 2023-11-14T22:13:20Z, running codex refreshes it"));
    }
}

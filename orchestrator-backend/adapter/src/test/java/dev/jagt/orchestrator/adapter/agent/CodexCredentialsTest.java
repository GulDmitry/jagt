package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.Host;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void answersFailedWhenTheStoredLoginIsNotJson() {
        var credential = CodexCredentials.parse("not json", NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Failed("stored login is not JSON"));
    }

    @Test
    void answersFailedWhenTheLoginCarriesNoAccessToken() {
        var credential = CodexCredentials.parse("{\"tokens\": {\"account_id\": \"acct-42\"}}", NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Failed("stored login carries no access token"));
    }

    @Test
    void answersFailedWhenNoSourceNamesTheAccount() {
        var credential = CodexCredentials.parse("{\"tokens\": {\"access_token\": \"opaque\"}}", NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Failed("stored login names no account"));
    }

    @Test
    void prefersTheAccountTheLoginNamesOverTheTokensClaim() {
        var credential = CodexCredentials.parse("""
                {"tokens": {"access_token": "h.eyJodHRwczovL2FwaS5vcGVuYWkuY29tL2F1dGgiOnsiY2hhdGdwdF9hY2NvdW50X2lkIjoiYWNjdC03In19.s",
                            "account_id": "acct-1"}}
                """, NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Token(
                "h.eyJodHRwczovL2FwaS5vcGVuYWkuY29tL2F1dGgiOnsiY2hhdGdwdF9hY2NvdW50X2lkIjoiYWNjdC03In19.s", "acct-1"));
    }

    @Test
    void readsTheClaimsOfATokenWithoutASignature() {
        var credential = CodexCredentials.parse("""
                {"tokens": {"access_token": "h.eyJodHRwczovL2FwaS5vcGVuYWkuY29tL2F1dGgiOnsiY2hhdGdwdF9hY2NvdW50X2lkIjoiYWNjdC03In19"}}
                """, NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Token(
                "h.eyJodHRwczovL2FwaS5vcGVuYWkuY29tL2F1dGgiOnsiY2hhdGdwdF9hY2NvdW50X2lkIjoiYWNjdC03In19", "acct-7"));
    }

    @Test
    void treatsATokenExpiringAtThisInstantAsExpired() {
        var credential = CodexCredentials.parse("""
                {"tokens": {"access_token": "h.eyJleHAiOjE3OTE2MzM2MDB9.s", "account_id": "acct-42"}}
                """, NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Failed(
                "token expired at 2026-10-10T12:00:00Z, running codex refreshes it"));
    }

    @Test
    void readsTheLoginFromCodexHomeWhenTheHumanSetOne(@TempDir Path codexHome) throws Exception {
        Files.writeString(codexHome.resolve("auth.json"),
                "{\"tokens\": {\"access_token\": \"opaque\", \"account_id\": \"acct-42\"}}");
        Host host = mock(Host.class);
        when(host.env("CODEX_HOME")).thenReturn(Optional.of(codexHome.toString()));

        var credential = new CodexCredentials(host).read(NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Token("opaque", "acct-42"));
    }

    @Test
    void readsTheLoginFromDotCodexInTheHomeOtherwise(@TempDir Path home) throws Exception {
        Files.createDirectory(home.resolve(".codex"));
        Files.writeString(home.resolve(".codex").resolve("auth.json"),
                "{\"tokens\": {\"access_token\": \"opaque\", \"account_id\": \"acct-42\"}}");
        Host host = mock(Host.class);
        when(host.env("CODEX_HOME")).thenReturn(Optional.empty());
        when(host.home()).thenReturn(home);

        var credential = new CodexCredentials(host).read(NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Token("opaque", "acct-42"));
    }

    @Test
    void answersNoSubscriptionWhenThereIsNoLoginFile(@TempDir Path home) {
        Host host = mock(Host.class);
        when(host.env("CODEX_HOME")).thenReturn(Optional.empty());
        when(host.home()).thenReturn(home);

        var credential = new CodexCredentials(host).read(NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.None());
    }

    @Test
    void answersFailedNamingTheErrorWhenTheLoginFileCannotBeRead(@TempDir Path codexHome) throws Exception {
        Files.createDirectory(codexHome.resolve("auth.json"));
        Host host = mock(Host.class);
        when(host.env("CODEX_HOME")).thenReturn(Optional.of(codexHome.toString()));

        var credential = new CodexCredentials(host).read(NOW);

        assertThat(credential).isEqualTo(new OAuthCredential.Failed("auth file unreadable IOException"));
    }
}

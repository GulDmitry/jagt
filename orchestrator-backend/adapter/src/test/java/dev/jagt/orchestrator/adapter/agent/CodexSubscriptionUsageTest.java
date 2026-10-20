package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodexSubscriptionUsageTest {

    @Test
    void sendsTheStoredTokenWithItsAccount() {
        CodexCredentials credentials = mock(CodexCredentials.class);
        UsageEndpoint endpoint = mock(UsageEndpoint.class);
        when(credentials.read(any())).thenReturn(new OAuthCredential.Token("tok-abc", "acct-42"));
        when(endpoint.get(any(), anyMap())).thenReturn(new UsageEndpoint.Body("{}"));

        new CodexSubscriptionUsage(credentials, endpoint).read();

        verify(endpoint).get(eq(CodexSubscriptionUsage.USAGE),
                eq(Map.of("Authorization", "Bearer tok-abc", "ChatGPT-Account-Id", "acct-42")));
    }

    @ParameterizedTest
    @CsvSource({
            "401, http 401, 'http 401, running codex refreshes the token'",
            "403, http 403, 'http 403, running codex refreshes the token'",
            "500, http 500, http 500",
    })
    void asksForANewLoginOnlyWhenTheTokenWasRefused(int status, String cause, String expected) {
        CodexCredentials credentials = mock(CodexCredentials.class);
        UsageEndpoint endpoint = mock(UsageEndpoint.class);
        when(credentials.read(any())).thenReturn(new OAuthCredential.Token("tok-abc", "acct-42"));
        when(endpoint.get(any(), anyMap())).thenReturn(new UsageEndpoint.Refused(status, cause));

        var answer = new CodexSubscriptionUsage(credentials, endpoint).read();

        assertThat(answer).isEqualTo(new SubscriptionUsage.Unreadable(expected));
    }

    @Test
    void answersNoSubscriptionWhenNoCredentialIsStored() {
        CodexCredentials credentials = mock(CodexCredentials.class);
        UsageEndpoint endpoint = mock(UsageEndpoint.class);
        when(credentials.read(any())).thenReturn(new OAuthCredential.None());

        var answer = new CodexSubscriptionUsage(credentials, endpoint).read();

        assertThat(answer).isInstanceOf(SubscriptionUsage.NoSubscription.class);
    }
}

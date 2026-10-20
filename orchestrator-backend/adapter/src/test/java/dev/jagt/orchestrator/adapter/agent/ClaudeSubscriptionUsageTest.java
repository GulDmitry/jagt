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

class ClaudeSubscriptionUsageTest {

    @Test
    void sendsTheStoredTokenWithTheOauthBetaHeader() {
        ClaudeCredentials credentials = mock(ClaudeCredentials.class);
        UsageEndpoint endpoint = mock(UsageEndpoint.class);
        when(credentials.read(any())).thenReturn(new OAuthCredential.Token("tok-abc", ""));
        when(endpoint.get(any(), anyMap())).thenReturn(new UsageEndpoint.Body("{}"));

        new ClaudeSubscriptionUsage(credentials, endpoint).read();

        verify(endpoint).get(eq(ClaudeSubscriptionUsage.USAGE),
                eq(Map.of("Authorization", "Bearer tok-abc", "anthropic-beta", "oauth-2025-04-20")));
    }

    @Test
    void answersUnreadableNamingTheRefreshWhenTheTokenIsRefused() {
        ClaudeCredentials credentials = mock(ClaudeCredentials.class);
        UsageEndpoint endpoint = mock(UsageEndpoint.class);
        when(credentials.read(any())).thenReturn(new OAuthCredential.Token("tok-abc", ""));
        when(endpoint.get(any(), anyMap())).thenReturn(new UsageEndpoint.Refused(401, "http 401"));

        var answer = new ClaudeSubscriptionUsage(credentials, endpoint).read();

        assertThat(answer).isEqualTo(new SubscriptionUsage.Unreadable("http 401, running claude refreshes the token"));
    }

    @Test
    void answersNoSubscriptionWithoutCallingTheEndpointForALoginHoldingNone() {
        ClaudeCredentials credentials = mock(ClaudeCredentials.class);
        UsageEndpoint endpoint = mock(UsageEndpoint.class);
        when(credentials.read(any())).thenReturn(new OAuthCredential.None());

        var answer = new ClaudeSubscriptionUsage(credentials, endpoint).read();

        assertThat(answer).isEqualTo(new SubscriptionUsage.NoSubscription());
    }

    @ParameterizedTest
    @CsvSource({
            "403, http 403, 'http 403, running claude refreshes the token'",
            "500, http 500, http 500",
    })
    void asksForANewLoginOnlyWhenTheTokenWasRefused(int status, String cause, String expected) {
        ClaudeCredentials credentials = mock(ClaudeCredentials.class);
        UsageEndpoint endpoint = mock(UsageEndpoint.class);
        when(credentials.read(any())).thenReturn(new OAuthCredential.Token("tok-abc", ""));
        when(endpoint.get(any(), anyMap())).thenReturn(new UsageEndpoint.Refused(status, cause));

        var answer = new ClaudeSubscriptionUsage(credentials, endpoint).read();

        assertThat(answer).isEqualTo(new SubscriptionUsage.Unreadable(expected));
    }
}

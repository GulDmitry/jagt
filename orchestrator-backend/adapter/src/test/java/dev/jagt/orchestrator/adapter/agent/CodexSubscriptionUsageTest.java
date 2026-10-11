package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

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
}
